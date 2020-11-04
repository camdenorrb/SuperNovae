package dev.twelveoclock.supernovae.proto;

/*
import java.io.IOException;
import java.nio.ByteBuffer;

public class BreakdownTemp {

    public int write(ByteBuffer inBuf) {

        int length = inBuf.remaining();

        ByteBuffer out = this.inner.getWriteBuffer();
        ByteBuffer slowBuffer = ByteBuffer.allocate(20);

        int inPtr = inBuf.position();
        int inEnd = inPtr + length;

        while (inPtr < inEnd) {

            if (out.remaining() < 10) {

                //# Oops, we're out of space. We need at least 10
                //# bytes for the fast path, since we don't
                //# bounds-check on every byte.

                if (out == slowBuffer) {
                    int oldLimit = out.limit();
                    out.limit(out.position());
                    out.rewind();
                    this.inner.write(out);
                    out.limit(oldLimit);
                }

                out = slowBuffer;
                out.rewind();
            }

            int tagPos = out.position();
            out.position(tagPos + 1);


            Byte tag = null;

            for (int i = 0; i < 8; i++) {

                byte curByte = inBuf.get(inPtr);
                byte bitI = (byte) ((curByte != 0) ? 1 : 0);

                out.put(curByte);
                out.position(out.position() + bitI - 1);

                inPtr += 1;

                if (tag == null) {
                    tag = bitI;
                }
                else {
                    tag = (byte) ((bitI << i) | tag);
                }
            }


            byte tag = (byte)((bit0 << 0) | (bit1 << 1) | (bit2 << 2) | (bit3 << 3) |
                    (bit4 << 4) | (bit5 << 5) | (bit6 << 6) | (bit7 << 7));

            out.put(tagPos, tag);

            if (tag == 0) {
                //# An all-zero word is followed by a count of
                //# consecutive zero words (not including the first
                //# one).
                int runStart = inPtr;
                int limit = inEnd;
                if (limit - inPtr > 255 * 8) {
                    limit = inPtr + 255 * 8;
                }
                while(inPtr < limit && inBuf.getLong(inPtr) == 0){
                    inPtr += 8;
                }
                out.put((byte)((inPtr - runStart)/8));

            } else if (tag == (byte)0xff) {
                //# An all-nonzero word is followed by a count of
                //# consecutive uncompressed words, followed by the
                //# uncompressed words themselves.

                //# Count the number of consecutive words in the input
                //# which have no more than a single zero-byte. We look
                //# for at least two zeros because that's the point
                //# where our compression scheme becomes a net win.

                int runStart = inPtr;
                int limit = inEnd;
                if (limit - inPtr > 255 * 8) {
                    limit = inPtr + 255 * 8;
                }

                while (inPtr < limit) {
                    byte c = 0;
                    for (int ii = 0; ii < 8; ++ii) {
                        c += (inBuf.get(inPtr) == 0 ? 1 : 0);
                        inPtr += 1;
                    }
                    if (c >= 2) {
                        //# Un-read the word with multiple zeros, since
                        //# we'll want to compress that one.
                        inPtr -= 8;
                        break;
                    }
                }

                int count = inPtr - runStart;
                out.put((byte)(count / 8));

                if (count <= out.remaining()) {
                    //# There's enough space to memcpy.
                    inBuf.position(runStart);
                    ByteBuffer slice = inBuf.slice();
                    slice.limit(count);
                    out.put(slice);
                } else {
                    //# Input overruns the output buffer. We'll give it
                    //# to the output stream in one chunk and let it
                    //# decide what to do.

                    if (out == slowBuffer) {
                        int oldLimit = out.limit();
                        out.limit(out.position());
                        out.rewind();
                        this.inner.write(out);
                        out.limit(oldLimit);
                    }

                    inBuf.position(runStart);
                    ByteBuffer slice = inBuf.slice();
                    slice.limit(count);
                    while(slice.hasRemaining()) {
                        this.inner.write(slice);
                    }

                    out = this.inner.getWriteBuffer();
                }
            }
        }

        if (out == slowBuffer) {
            out.limit(out.position());
            out.rewind();
            this.inner.write(out);
        }

        inBuf.position(inPtr);
        return length;
    }
}
*/
