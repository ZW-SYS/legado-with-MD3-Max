package io.legato.kazusa.service

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseService
import io.legato.kazusa.constant.AppConst
import io.legato.kazusa.constant.AppLog
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.constant.IntentAction
import io.legato.kazusa.constant.NotificationId
import io.legato.kazusa.data.appDb
import io.legato.kazusa.help.book.update
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.model.CacheBook
import io.legato.kazusa.model.webBook.WebBook
import io.legato.kazusa.ui.book.cache.CacheActivity
import io.legato.kazusa.utils.activityPendingIntent
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.servicePendingIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * 缓存书籍服务
 */
class CacheBookService : BaseService() {

    companion object {
        var isRun = false
            private set
    }

    private val threadCount = AppConfig.threadCount
    private var cachePool =
        Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    private var downloadJob: Job? = null
    private var notificationContent = appCtx.getString(R.string.service_starting)
    private var mutex = Mutex()
    private val notificationBuilder by lazy {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.offline_cache))
            .setContentIntent(activityPendingIntent<CacheActivity>("cacheActivity"))
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<CacheBookService>(IntentAction.stop)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                notificationContent = CacheBook.downloadSummary
                upCacheBookNotification()
                postEvent(EventBus.UP_DOWNLOAD, "")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.start -> {
                    val bookUrl = intent.getStringExtra("bookUrl") ?: return@let
                    val indices = intent.getIntegerArrayListExtra("indices")
                    if (indices != null && indices.isNotEmpty()) {
                        addDownloadData(bookUrl, indices)
                    } else {
                        addDownloadData(
                            bookUrl,
                            intent.getIntExtra("start", 0),
                            intent.getIntExtra("end", 0)
                        )
                    }
                }
                IntentAction.remove -> {
                    val bookUrl = intent.getStringExtra("bookUrl")
                    CacheBook.cacheBookMap[bookUrl]?.stop()
                    CacheBook.cacheBookMap.remove(bookUrl)
                }
                IntentAction.stop -> stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        isRun = false
        cachePool.close()
        CacheBook.close()
        super.onDestroy()
        postEvent(EventBus.UP_DOWNLOAD, "")
    }

    private fun addDownloadData(bookUrl: String?, indices: List<Int>) {
        bookUrl ?: return
        if (indices.isEmpty()) return

        execute {
            val cacheBook = CacheBook.getOrCreate(bookUrl) ?: return@execute

            val book = cacheBook.book
            val chapterCount = appDb.bookChapterDao.getChapterCount(bookUrl)

            if (chapterCount == 0) {
                mutex.withLock {
                    val name = book.name
                    if (book.tocUrl.isEmpty()) {
                        kotlin.runCatching {
                            WebBook.getBookInfoAwait(cacheBook.bookSource, book)
                        }.onFailure {
                            AppLog.put(
                                "《$name》目录为空且加载详情页失败\n${it.localizedMessage}",
                                it,
                                true
                            )
                            return@execute
                        }
                    }

                    WebBook.getChapterListAwait(cacheBook.bookSource, book).onFailure {
                        if (book.totalChapterNum > 0) {
                            book.totalChapterNum = 0
                            book.update()
                        }
                        AppLog.put(
                            "《$name》目录为空且加载目录失败\n${it.localizedMessage}",
                            it,
                            true
                        )
                        return@execute
                    }.getOrNull()?.let { toc ->
                        appDb.bookChapterDao.insert(*toc.toTypedArray())
                    }

                    book.update()
                }
            }

            //添加每一章到下载队列
            indices.forEach { index ->
                cacheBook.addDownload(index)
            }

            notificationContent = CacheBook.downloadSummary
            upCacheBookNotification()
        }.onFinally {
            if (downloadJob == null) {
                download()
            }
        }
    }

    private fun addDownloadData(bookUrl: String?, start: Int, end: Int) {
        addDownloadData(bookUrl, (start..end).toList())
    }


    private fun removeDownload(bookUrl: String?) {
        CacheBook.cacheBookMap[bookUrl]?.stop()
        postEvent(EventBus.UP_DOWNLOAD, "")
        if (downloadJob == null && CacheBook.isRun) {
            download()
            return
        }
        if (CacheBook.cacheBookMap.isEmpty()) {
            stopSelf()
        }
    }

    private fun download() {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch(cachePool) {
            while (isActive) {
                if (!CacheBook.isRun) {
                    stopSelf()
                    return@launch
                }
                CacheBook.cacheBookMap.forEach {
                    val cacheBookModel = it.value
                    while (cacheBookModel.waitCount > 0) {
                        if (CacheBook.onDownloadCount < threadCount) {
                            cacheBookModel.download(this, cachePool)
                        } else {
                            delay(100)
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun upCacheBookNotification() {
        notificationBuilder.setContentText(notificationContent)
        val notification = notificationBuilder.build()
        notificationManager.notify(NotificationId.CacheBookService, notification)
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        notificationBuilder.setContentText(notificationContent)
        val notification = notificationBuilder.build()
        startForeground(NotificationId.CacheBookService, notification)
    }

}