package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import java.io.OutputStream;

public class ProtobufUtil {

    /**
     * Creates a CodedOutputStream with a buffer size adjusted for the message to be serialized,
     * limited to the maximum buffer size. We size the buffer to include space for the
     * size of the delimiter.
     *
     * @param outputStream the output stream to write to
     * @param messageSize  the size of the message to be written
     * @return a new CodedOutputStream instance
     */
    public static CodedOutputStream createCodedOutputStream(OutputStream outputStream, int messageSize) {
        final int bufferSize = Integer.min(
            CodedOutputStream.computeUInt32SizeNoTag(messageSize) + messageSize,
            ProtoMessage.MAX_OUTPUT_STREAM_BUFFER_SIZE
        );
        return CodedOutputStream.newInstance(outputStream, bufferSize);
    }

    /**
     * Creates a CodedOutputStream with a default (maximum) buffer size. Use this method when
     * you want to reuse the CodedOutputStream for multiple messages and you don't know the
     * size of the messages in advance.
     *
     * @param outputStream the output stream to write to
     * @return a new CodedOutputStream instance with the maximum buffer size
     */
    public static CodedOutputStream createCodedOutputStream(OutputStream outputStream) {
        return CodedOutputStream.newInstance(outputStream, ProtoMessage.MAX_OUTPUT_STREAM_BUFFER_SIZE);
    }
}
