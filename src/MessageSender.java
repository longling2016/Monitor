import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Created by longlingwang on 5/31/17.
 */
public class MessageSender {

    public void send (String message, String ip, int port) {
        try {
//            System.out.println("send message: " + message);
            Socket s = new Socket(ip, port);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.writeUTF(message);
            out.flush();
            out.close();
            s.close();
        } catch (IOException e) {
            System.out.println("node " + ip + ": " + port + " can't be reached!");
        }
    }
}
