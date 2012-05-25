package tc.tools.deployer.ipa;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Stack;

public class ElephantMemoryReader extends ByteArrayInputStream implements ElephantMemoryStream
{
   Stack positions = new Stack();

   boolean bStreamLittleEndian = true;

   public ElephantMemoryReader(byte[] data)
   {
      super(data);
   }

   public long readUnsignedInt() throws IOException
   {
      byte[] b = new byte[4];
      read(b);

      if (bStreamLittleEndian)
         return ((((long) (b[3] & 0xFF)) << 24) | (((long) (b[2] & 0xFF)) << 16) | ((b[1] & 0xFF) << 8) | (b[0] & 0xFF));
      return ((((long) (b[0] & 0xFF)) << 24) | (((long) (b[1] & 0xFF)) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF));
   }

   public String readString(int maxLength) throws IOException
   {
      byte[] b = new byte[maxLength];
      read(b);
      int actualLength = maxLength;
      for (int i = 0; i < b.length; i++)
      {
         if (b[i] == 0)
            actualLength = i;
      }
      return new String(b, 0, actualLength, "US-ASCII");
   }

   public String readString() throws IOException
   {
      int startPos = pos;
      byte oneByte;
      do
      {
         oneByte = (byte) this.read();
      } while (oneByte != 0);

      byte[] b = new byte[(int) (pos - startPos)];
      skip(startPos - pos);
      pos = startPos;
      read(b);
      return new String(b, 0, b.length, "US-ASCII");
   }

   public void moveTo(int newPosition)
   {
      reset();
      skip(newPosition);
   }

   public void moveTo(long newPosition)
   {
      reset();
      skip(newPosition);
   }

   public void memorize()
   {
      positions.push(Integer.valueOf(this.pos));
   }

   public void moveBack()
   {
      this.pos = ((Integer) positions.pop()).intValue();
   }

   public int getPos()
   {
      return this.pos;
   }
}
