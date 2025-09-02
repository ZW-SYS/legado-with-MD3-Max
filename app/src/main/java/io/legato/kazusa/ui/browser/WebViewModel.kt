package io.legato.kazusa.ui.browser

import android.app.Application
import android.content.Intent
import android.util.Base64
import android.webkit.URLUtil
import android.webkit.WebView
import io.legato.kazusa.base.BaseViewModel
import io.legato.kazusa.constant.AppConst.imagePathKey
import io.legato.kazusa.constant.SourceType
import io.legato.kazusa.data.appDb
import io.legato.kazusa.exception.NoStackTraceException
import io.legato.kazusa.help.http.newCallResponseBody
import io.legato.kazusa.help.http.okHttpClient
import io.legato.kazusa.help.source.SourceHelp
import io.legato.kazusa.help.source.SourceVerificationHelp
import io.legato.kazusa.model.analyzeRule.AnalyzeUrl
import io.legato.kazusa.utils.ACache
import io.legato.kazusa.utils.ImageSaveUtils
import io.legato.kazusa.utils.printOnDebug
import io.legato.kazusa.utils.toastOnUi
import org.apache.commons.text.StringEscapeUtils

class WebViewModel(application: Application) : BaseViewModel(application) {
    var intent: Intent? = null
    var baseUrl: String = ""
    var html: String? = null
    val headerMap: HashMap<String, String> = hashMapOf()
    var sourceVerificationEnable: Boolean = false
    var refetchAfterSuccess: Boolean = true
    var sourceName: String = ""
    var sourceOrigin: String = ""
    var sourceType = SourceType.book

    fun initData(
        intent: Intent,
        success: () -> Unit
    ) {
        execute {
            this@WebViewModel.intent = intent
            val url = intent.getStringExtra("url")
                ?: throw NoStackTraceException("url不能为空")
            sourceName = intent.getStringExtra("sourceName") ?: ""
            sourceOrigin = intent.getStringExtra("sourceOrigin") ?: ""
            sourceType = intent.getIntExtra("sourceType", SourceType.book)
            sourceVerificationEnable = intent.getBooleanExtra("sourceVerificationEnable", false)
            refetchAfterSuccess = intent.getBooleanExtra("refetchAfterSuccess", true)
            val source = SourceHelp.getSource(sourceOrigin, sourceType)
            val analyzeUrl = AnalyzeUrl(url, source = source, coroutineContext = coroutineContext)
            baseUrl = analyzeUrl.url
            headerMap.putAll(analyzeUrl.headerMap)
            if (analyzeUrl.isPost()) {
                html = analyzeUrl.getStrResponseAwait(useWebView = false).body
            }
        }.onSuccess {
            success.invoke()
        }.onError {
            context.toastOnUi("error\n${it.localizedMessage}")
            it.printOnDebug()
        }
    }

    fun saveImage(webPic: String?) {
        webPic ?: return
        execute {
            val byteArray = webData2bitmap(webPic) ?: throw Throwable("NULL")

            val success = ImageSaveUtils.saveImageToGallery(
                context,
                byteArray,
                folderName = "Legado"
            )

            if (!success) throw Throwable("保存到相册失败")
        }.onError {
            ACache.get().remove(imagePathKey)
            context.toastOnUi("保存图片失败: ${it.localizedMessage}")
        }.onSuccess {
            context.toastOnUi("已保存到相册")
        }
    }

    private suspend fun webData2bitmap(data: String): ByteArray? {
        return if (URLUtil.isValidUrl(data)) {
            okHttpClient.newCallResponseBody {
                url(data)
            }.bytes()
        } else {
            Base64.decode(data.split(",").toTypedArray()[1], Base64.DEFAULT)
        }
    }

    fun saveVerificationResult(webView: WebView, success: () -> Unit) {
        if (!sourceVerificationEnable) {
            return success.invoke()
        }
        if (refetchAfterSuccess) {
            execute {
                val url = intent!!.getStringExtra("url")!!
                val source = appDb.bookSourceDao.getBookSource(sourceOrigin)
                html = AnalyzeUrl(
                    url,
                    headerMapF = headerMap,
                    source = source,
                    coroutineContext = coroutineContext
                ).getStrResponseAwait(useWebView = false).body
                SourceVerificationHelp.setResult(sourceOrigin, html ?: "")
            }.onSuccess {
                success.invoke()
            }
        } else {
            webView.evaluateJavascript("document.documentElement.outerHTML") {
                execute {
                    html = StringEscapeUtils.unescapeJson(it).trim('"')
                    SourceVerificationHelp.setResult(sourceOrigin, html ?: "")
                }.onSuccess {
                    success.invoke()
                }
            }
        }
    }

    fun disableSource(block: () -> Unit) {
        execute {
            SourceHelp.enableSource(sourceOrigin, sourceType, false)
        }.onSuccess {
            block.invoke()
        }
    }

    fun deleteSource(block: () -> Unit) {
        execute {
            SourceHelp.deleteSource(sourceOrigin, sourceType)
        }.onSuccess {
            block.invoke()
        }
    }

}