package burp

import java.util.*


class BurpExtender: IBurpExtender, IExtensionStateListener {
    companion object {
        lateinit var callbacks: IBurpExtenderCallbacks
    }
    private lateinit var highlighterThread: HighlighterThread

    override fun registerExtenderCallbacks(callbacks: IBurpExtenderCallbacks) {
        Companion.callbacks = callbacks
        callbacks.setExtensionName("Highlight Discovered Content")
        highlighterThread = HighlighterThread().apply { start() }
        callbacks.registerHttpListener(HttpListener(highlighterThread))
        callbacks.registerExtensionStateListener(this)
    }

    override fun extensionUnloaded() {
        highlighterThread.terminating = true
    }
}


class HttpListener(private val highlighterThread: HighlighterThread): IHttpListener {
    override fun processHttpMessage(toolFlag: Int, messageIsRequest: Boolean, messageInfo: IHttpRequestResponse) {
        if(messageIsRequest) {
            return
        }
        try {
            if(toolFlag == IBurpExtenderCallbacks.TOOL_TARGET) {
                val url = getUrl(messageInfo)
                var found = false
                for(requestResponse in BurpExtender.callbacks.getSiteMap(url)) {
                    if(getUrl(requestResponse) == url) {
                        found = true
                        break
                    }
                }
                if(!found) {
                    highlighterThread.queue.add(RequestToHighlight(url))
                }
            }
        }
        catch(ex: Exception) {
            BurpExtender.callbacks.printError(ex.toString())
            ex.printStackTrace()
        }
    }
}


class RequestToHighlight(
    val url: String
) {
    val time = System.currentTimeMillis()
}


class HighlighterThread() : Thread() {
    val queue = LinkedList<RequestToHighlight>()
    var terminating = false
    private val quantum = 1000.toLong()

    override fun run() {
        while(!terminating) {
            val requestToHighlight = queue.poll()
            if(requestToHighlight == null) {
                Thread.sleep(quantum)
                continue
            }
            val timeDifference = System.currentTimeMillis() - requestToHighlight.time
            if(timeDifference < quantum) {
                Thread.sleep(quantum - timeDifference)
            }

            for(requestResponse in BurpExtender.callbacks.getSiteMap(requestToHighlight.url)) {
                val url = getUrl(requestResponse)
                if(url == requestToHighlight.url) {
                    requestResponse.highlight = "cyan"
                    break
                }
            }
        }
    }
}


fun getUrl(requestResponse: IHttpRequestResponse): String {
    val requestInfo = BurpExtender.callbacks.helpers.analyzeRequest(requestResponse.httpService, requestResponse.request)
    return requestInfo.url.toString()
}
