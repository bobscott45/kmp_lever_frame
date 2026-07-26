package org.edranor.openlcb

interface OpenLcbConfig {
    val nodeId: String
    val nodeName: String
    val jmriHubIp: String
}

interface MemorySpaceHandler {
    fun getCdiXml(): ByteArray
    fun readMemorySpace(space: Int): ByteArray
    fun writeMemorySpace(space: Int, address: Long, data: ByteArray)
}

interface EventProducerProvider {
    fun getProducedEvents(): List<String>
}
