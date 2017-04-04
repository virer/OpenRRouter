package orv.Router;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class lib {
	static final String version = "0.01";
	public static final byte clientType_rcuser = 0;
    public static final byte clientType_manager = 1;
	
	ByteBuffer integersToByteBuffer(int[] data) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
		IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);
        return byteBuffer;
	} 
	
	byte[] integersToBytes(int[] data) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
		IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);

        byte[] array = byteBuffer.array();
        byteBuffer = null;
        intBuffer = null;
        return array;
	} 
	
	int[] bytesToIntegers(byte[] input) {
		int[] ret = new int[input.length];
	    for (int i = 0; i < input.length; i++) {
	        ret[i] = input[i] & 0xff; // Range 0 to 255, not -128 to 127
	    }
	    return ret;
	}

	static String genHttpHeaderReply(int size){
		String date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date()).toString().replaceAll("\\.", "");
		return "HTTP/1.1 200 OK"
				+"Date: "+date.toUpperCase()				
				+"Content-Type: application/octet-stream"
				+"Accept-Ranges: bytes"
				+"Age: 0"
				+"ORV-Version: "+ version
				+"Cache-Control: private"
				+"Connection: keep-alive"
				+"Content-Encoding: gzip"
				+"Content-Length: "+size+"\n\n";
	}
	
	
	
}
