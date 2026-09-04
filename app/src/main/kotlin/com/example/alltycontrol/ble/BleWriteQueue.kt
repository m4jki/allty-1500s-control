package com.example.alltycontrol.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BleWriteQueue(
    scope: CoroutineScope,
    private val writeAction: suspend (ByteArray) -> BleWriteResult,
) {
    private data class Request(
        val frame: ByteArray,
        val postWriteDelayMillis: Long,
        val result: CompletableDeferred<BleWriteResult>,
    )

    private val requests = Channel<Request>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in requests) {
                if (!request.result.isActive) continue
                val result = writeAction(request.frame)
                request.result.complete(result)
                if (result == BleWriteResult.Success && request.postWriteDelayMillis > 0) {
                    delay(request.postWriteDelayMillis)
                }
            }
        }
    }

    suspend fun enqueue(frame: ByteArray, postWriteDelayMillis: Long = 0): BleWriteResult {
        require(postWriteDelayMillis >= 0)
        val result = CompletableDeferred<BleWriteResult>()
        requests.send(Request(frame.copyOf(), postWriteDelayMillis, result))
        return result.await()
    }
}
