package io.legato.kazusa.ui.about

//import io.legado.app.lib.theme.accentColor
//import io.legado.app.lib.theme.filletBackground
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseActivity
import io.legato.kazusa.constant.AppLog
import io.legato.kazusa.databinding.ActivityAboutBinding
import io.legato.kazusa.help.CrashHandler
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.update.AppUpdate
import io.legato.kazusa.ui.widget.dialog.TextDialog
import io.legato.kazusa.ui.widget.dialog.WaitDialog
import io.legato.kazusa.utils.FileDoc
import io.legato.kazusa.utils.compress.ZipUtils
import io.legato.kazusa.utils.createFileIfNotExist
import io.legato.kazusa.utils.createFolderIfNotExist
import io.legato.kazusa.utils.delete
import io.legato.kazusa.utils.externalCache
import io.legato.kazusa.utils.find
import io.legato.kazusa.utils.list
import io.legato.kazusa.utils.openInputStream
import io.legato.kazusa.utils.openOutputStream
import io.legato.kazusa.utils.openUrl
import io.legato.kazusa.utils.share
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.toastOnUi
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.delay
import splitties.init.appCtx
import java.io.File


class AboutActivity : BaseActivity<ActivityAboutBinding>() {

    override val binding by viewBinding(ActivityAboutBinding::inflate)

    private val waitDialog by lazy {
        WaitDialog(this).setText(R.string.checking_update)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.topBar)

        binding.btnUpdate.setOnClickListener {
            checkUpdate()
        }

        binding.btnGithub.setOnClickListener {
            openUrl(R.string.github_url)
        }

        binding.btnWeb.setOnClickListener {
            openUrl(R.string.legado_url)
        }

        binding.llContributors.setOnClickListener {
            openUrl(R.string.contributors_url)
        }

        binding.llUpdateLog.setOnClickListener {
            showMdFile(getString(R.string.update_log), "updateLog.md")
        }

        binding.llPrivacyPolicy.setOnClickListener {
            showMdFile(getString(R.string.privacy_policy), "privacyPolicy.md")
        }

        binding.llLicense.setOnClickListener {
            showMdFile(getString(R.string.license), "LICENSE.md")
        }

        binding.llDisclaimer.setOnClickListener {
            showMdFile(getString(R.string.disclaimer), "disclaimer.md")
        }

        binding.llCrashLog.setOnClickListener {
            showDialogFragment<CrashLogsDialog>()
        }

        binding.llSaveLog.setOnClickListener {
            saveLog()
        }

        binding.llCreateHeapDump.setOnClickListener {
            createHeapDump()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.about, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_share_it -> share(
                getString(R.string.app_share_description),
                getString(R.string.app_name)
            )
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun showMdFile(title: String, fileName: String) {
        val mdText = String(this.assets.open(fileName).readBytes())
        showDialogFragment(TextDialog(title, mdText, TextDialog.Mode.MD))
    }

    private fun checkUpdate() {
        waitDialog.show()
        AppUpdate.gitHubUpdate?.run {
            check(lifecycleScope)
                .onSuccess {
                    showDialogFragment(UpdateDialog(it))
                }.onError {
                    appCtx.toastOnUi("${getString(R.string.check_update)}\n${it.localizedMessage}")
                }.onFinally {
                    waitDialog.dismiss()
                }
        }
    }

    @Suppress("SameParameterValue")
    private fun openUrl(@StringRes addressID: Int) {
        this.openUrl(getString(addressID))
    }

    private fun saveLog() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi("未设置备份目录")
                return@async
            }
            if (!AppConfig.recordLog) {
                appCtx.toastOnUi("未开启日志记录，请去其他设置里打开记录日志")
                delay(3000)
            }
            val doc = FileDoc.fromUri(backupPath.toUri(), true)
            copyLogs(doc)
            copyHeapDump(doc)
            appCtx.toastOnUi("已保存至备份目录")
        }.onError {
            AppLog.put("保存日志出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun createHeapDump() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi("未设置备份目录")
                return@async
            }
            if (!AppConfig.recordHeapDump) {
                appCtx.toastOnUi("未开启堆转储记录，请去其他设置里打开记录堆转储")
                delay(3000)
            }
            appCtx.toastOnUi("开始创建堆转储")
            System.gc()
            CrashHandler.doHeapDump(true)
            val doc = FileDoc.fromUri(backupPath.toUri(), true)
            if (!copyHeapDump(doc)) {
                appCtx.toastOnUi("未找到堆转储文件")
            } else {
                appCtx.toastOnUi("已保存至备份目录")
            }
        }.onError {
            AppLog.put("保存堆转储失败\n${it.localizedMessage}", it)
        }
    }

    private fun copyLogs(doc: FileDoc) {
        val cacheDir = appCtx.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")

        dumpLogcat(logcatFile)

        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)

        doc.find("logs.zip")?.delete()

        zipFile.inputStream().use { input ->
            doc.createFileIfNotExist("logs.zip").openOutputStream().getOrNull()
                ?.use {
                    input.copyTo(it)
                }
        }
        zipFile.delete()
    }

    private fun copyHeapDump(doc: FileDoc): Boolean {
        val heapFile = FileDoc.fromFile(File(appCtx.externalCache, "heapDump")).list()
            ?.firstOrNull() ?: return false
        doc.find("heapDump")?.delete()
        val heapDumpDoc = doc.createFolderIfNotExist("heapDump")
        heapFile.openInputStream().getOrNull()?.use { input ->
            heapDumpDoc.createFileIfNotExist(heapFile.name).openOutputStream().getOrNull()
                ?.use {
                    input.copyTo(it)
                }
        }
        return true
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use {
                process.inputStream.copyTo(it)
            }
        } catch (e: Exception) {
            AppLog.put("保存Logcat失败\n$e", e)
        }
    }

}
