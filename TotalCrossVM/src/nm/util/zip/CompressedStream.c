/*********************************************************************************
 *  TotalCross Software Development Kit                                          *
 *  Copyright (C) 2000-2012 SuperWaba Ltda.                                      *
 *  All Rights Reserved                                                          *
 *                                                                               *
 *  This library and virtual machine is distributed in the hope that it will     *
 *  be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of    *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         *
 *                                                                               *
 *********************************************************************************/



#include "tcvm.h"
#include "../../../zlib/zutil.h"

#define BUFSIZE   0x400

enum
{
   DEFLATE = 1,
   INFLATE
};

static voidpf zalloc(voidpf opaque, uInt items, uInt size)
{
   UNUSED(opaque)
   return xmalloc(items*size);
}

static void zfree(voidpf opaque, voidpf address)
{
   UNUSED(opaque)
   xfree(address);
}

typedef struct
{
   Object stream;
   Method rwMethod;
   z_stream c_stream; // compression stream
} TZLibStreamRef, *ZLibStreamRef;

//////////////////////////////////////////////////////////////////////////
TC_API void tuzCS_createInflate_s(NMParams p) // totalcross/util/zip/CompressedStream native protected Object createInflate(totalcross.io.Stream stream) throws IOException;
{
   Object stream = p->obj[1];
   Object zstreamRefObj;
   ZLibStreamRef zstreamRef;
   Err err = Z_OK;

   if ((zstreamRefObj = createByteArray(p->currentContext, sizeof(TZLibStreamRef))) != null)
   {
      zstreamRef = (ZLibStreamRef) ARRAYOBJ_START(zstreamRefObj);
      zstreamRef->stream = stream;       
      zstreamRef->c_stream.zalloc = zalloc;
      zstreamRef->c_stream.zfree = zfree;
      zstreamRef->c_stream.opaque = (voidpf) 0;
      zstreamRef->c_stream.next_in = Z_NULL;
      zstreamRef->c_stream.avail_in = 0;

      if ((err = inflateInit(&zstreamRef->c_stream)) != Z_OK)
         throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
      else
      {
         zstreamRef->rwMethod  = getMethod((Class) OBJ_CLASS(stream), true, "readBytes", 3, BYTE_ARRAY, J_INT, J_INT);
         p->retO = zstreamRefObj;
      }
   }   
}
//////////////////////////////////////////////////////////////////////////
TC_API void tuzCS_createDeflate_si(NMParams p) // totalcross/util/zip/CompressedStream native protected Object createDeflate(totalcross.io.Stream stream, int compressionType) throws IOException;
{
   Object stream = p->obj[1];
   int32 compressionType = p->i32[0];
   Object zstreamRefObj;
   ZLibStreamRef zstreamRef;
   Err err = Z_OK;

   if ((zstreamRefObj = createByteArray(p->currentContext, sizeof(TZLibStreamRef))) != null)
   {
      zstreamRef = (ZLibStreamRef) ARRAYOBJ_START(zstreamRefObj);
      zstreamRef->stream = stream;       
      zstreamRef->c_stream.zalloc = zalloc;
      zstreamRef->c_stream.zfree = zfree;
      zstreamRef->c_stream.opaque = (voidpf) 0;

      if ((err = deflateInit2(&zstreamRef->c_stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, compressionType + MAX_WBITS, DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY)) != Z_OK) //flsobral@tc114_82: now supports GZip compression.
         throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
      else
      {
         zstreamRef->rwMethod  = getMethod((Class) OBJ_CLASS(stream), true, "writeBytes", 3, BYTE_ARRAY, J_INT, J_INT);
         p->retO = zstreamRefObj;
      }
   }
}
//////////////////////////////////////////////////////////////////////////
TC_API void tuzCS_readBytes_Bii(NMParams p) // totalcross/util/zip/CompressedStream native public int readBytes(byte []buf, int start, int count) throws IOException;
{
   Object zstreamRefObj = CompressedStream_streamRef(p->obj[0]);
   Object streamBuffer = CompressedStream_streamBuffer(p->obj[0]);
   int32 mode = CompressedStream_mode(p->obj[0]);
   ZLibStreamRef zstreamRef = (ZLibStreamRef) ARRAYOBJ_START(zstreamRefObj);
   Object stream = zstreamRef->stream;
   Object buf = p->obj[1];
   int32 start = p->i32[0];
   int32 count = p->i32[1];
   int32 end = start + count;
   int32 toCopy;
   int32 result = 0;
   Err err = Z_OK;

   if (mode != INFLATE)
      throwException(p->currentContext, IOException, "This operation can only be performed in INFLATE mode.");
   else
   {
      while (start < end && err != Z_STREAM_END)
      {
         if (zstreamRef->c_stream.avail_in == 0)
         {
            zstreamRef->c_stream.avail_in = executeMethod(p->currentContext, zstreamRef->rwMethod, stream, streamBuffer, 0, ARRAYOBJ_LEN(streamBuffer)).asInt32;
            zstreamRef->c_stream.next_in = ARRAYOBJ_START(streamBuffer);
         }
         zstreamRef->c_stream.next_out = ARRAYOBJ_START(buf) + start;
         toCopy = end - start;
         zstreamRef->c_stream.avail_out = toCopy;
         err = inflate(&zstreamRef->c_stream, Z_NO_FLUSH);
         if (err != Z_OK && err != Z_STREAM_END)
            throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
         start += toCopy - zstreamRef->c_stream.avail_out;
         result += toCopy - zstreamRef->c_stream.avail_out;
      }
      p->retI = result;
   }   
}
//////////////////////////////////////////////////////////////////////////
TC_API void tuzCS_writeBytes_Bii(NMParams p) // totalcross/util/zip/CompressedStream native public int writeBytes(byte []buf, int start, int count) throws IOException;
{
   Object zstreamRefObj = CompressedStream_streamRef(p->obj[0]);
   Object streamBuffer = CompressedStream_streamBuffer(p->obj[0]);
   int32 mode = CompressedStream_mode(p->obj[0]);
   ZLibStreamRef zstreamRef = (ZLibStreamRef) ARRAYOBJ_START(zstreamRefObj);
   Object stream = zstreamRef->stream;
   Object buf = p->obj[1];
   int32 start = p->i32[0];
   int32 count = p->i32[1];
   int32 streamBufferLen = ARRAYOBJ_LEN(streamBuffer);
   int32 result = 0;
   Err err = Z_OK;

   if (mode != DEFLATE)
      throwException(p->currentContext, IOException, "This operation can only be performed in DEFLATE mode.");
   else
   {
      zstreamRef->c_stream.next_in = ARRAYOBJ_START(buf) + start;
      zstreamRef->c_stream.avail_in = count;
      
      do
      {
         zstreamRef->c_stream.next_out = ARRAYOBJ_START(streamBuffer);
         zstreamRef->c_stream.avail_out = streamBufferLen;
         if ((err = deflate(&zstreamRef->c_stream, Z_NO_FLUSH)) != Z_OK)
            throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
         result += executeMethod(p->currentContext, zstreamRef->rwMethod, stream, streamBuffer, 0, streamBufferLen - zstreamRef->c_stream.avail_out).asInt32;
      }
      while (zstreamRef->c_stream.avail_out == 0);
      
      p->retI = result;
   }
}
//////////////////////////////////////////////////////////////////////////
TC_API void tuzCS_close(NMParams p) // totalcross/util/zip/CompressedStream native public void close() throws IOException;
{
   Object zstreamRefObj = CompressedStream_streamRef(p->obj[0]);
   Object streamBuffer = CompressedStream_streamBuffer(p->obj[0]);
   ZLibStreamRef zstreamRef = (ZLibStreamRef) ARRAYOBJ_START(zstreamRefObj);
   Object stream = zstreamRef->stream;
   int32 mode = CompressedStream_mode(p->obj[0]);
   int32 streamBufferLen = ARRAYOBJ_LEN(streamBuffer);
   Err err;

   CompressedStream_mode(p->obj[0]) = 0;
   switch (mode)
   {
      case DEFLATE:
      {
         do
         {
            zstreamRef->c_stream.next_out = ARRAYOBJ_START(streamBuffer);
            zstreamRef->c_stream.avail_out = streamBufferLen;
            err = deflate(&zstreamRef->c_stream, Z_FINISH);
            if (err != Z_OK && err != Z_STREAM_END)
               throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
            if ((int32) zstreamRef->c_stream.avail_out < streamBufferLen)
               executeMethod(p->currentContext, zstreamRef->rwMethod, stream, streamBuffer, 0, streamBufferLen - zstreamRef->c_stream.avail_out).asInt32;
         }
         while (zstreamRef->c_stream.avail_out == 0);
         if (err != Z_STREAM_END)
            throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
         if ((err = deflateEnd(&zstreamRef->c_stream)) != Z_OK)
            throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
      }
      break;
      case INFLATE:
         if ((err = inflateEnd(&zstreamRef->c_stream)) != Z_OK)
            throwException(p->currentContext, IOException, zstreamRef->c_stream.msg);
      break;
      default:
         throwException(p->currentContext, IOException, "Invalid object.");
      break;      
   }   
}

#ifdef ENABLE_TEST_SUITE
//#include "zip_ZLib_test.h"
#endif