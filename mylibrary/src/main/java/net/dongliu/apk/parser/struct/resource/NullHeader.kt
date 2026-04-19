package net.dongliu.apk.parser.struct.resource

import net.dongliu.apk.parser.struct.ChunkHeader

class NullHeader(chunkType: Int, headerSize: Int, chunkSize: Int) :
    ChunkHeader(chunkType, headerSize, chunkSize.toLong())
