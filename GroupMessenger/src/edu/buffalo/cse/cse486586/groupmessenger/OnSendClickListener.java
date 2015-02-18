package edu.buffalo.cse.cse486586.groupmessenger;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

/*====================================================================
 * Class name  : OnSendClickListener implements OnClickListener
 * Description : This class takes care of operations to be done 
 * 					when the send button is clicked
 * Authors	   : Rajaram Rabindranath
 *===================================================================*/
public class OnSendClickListener implements OnClickListener 
{
	
    private static final String TAG = OnSendClickListener.class.getName();
    private static int my_msgCnt = 0;
	private final EditText msgTextBox;
	private final String myPort;
	private final int AVD_num;
	static final String[] remotePorts = {"11120","11108","11112","11124","11116"}; // the mapping of all ports
	
	public OnSendClickListener(EditText msgTextBox,String myPort)
    {
    	Log.d(TAG,"my constructor has been called");
    	this.msgTextBox = msgTextBox;
    	this.myPort = myPort;
    	AVD_num = (Integer.parseInt(myPort)/2);
	}

    @Override
	/*====================================================================
     * Function    : onClick
     * Description : gets the message on the message edit box and Bcasts
     * 					the msg in a thread to all AVDs and then waits for
     * 					the send operation to complete
     * Parameters  : View v
     * Return 	   : void
     *===================================================================*/
	public void onClick(View v) 
	{
		Log.e(TAG,"the send button has been clicked");
        String msg = msgTextBox.getText().toString() + "\n";
        msgTextBox.setText("");
        
        update_myMsgCnt(); // and thereby my vector clock
        String metaData = "@:@"+GroupMessengerActivity.get_vectClkInfo();
        String msgToSend = AVD_num+"_"+get_myMsgCnt()+":"+msg+metaData; // each message has a sender's marker
        Bcast sendMsg_sequentially = new Bcast(msgToSend);
        sendMsg_sequentially.start();
        try
        {
        	sendMsg_sequentially.join();
        }
        catch(InterruptedException ieEx)
        {
        	ieEx.printStackTrace();
        	Log.e(TAG,"Problems in Bcasting messages to other AVDs");
        }
    	return;
	}

	/*====================================================================
     * Class name  : Bcast
     * Description : Extends Thread and has implemented run() to send
     * 					send messages to other AVDs
     * Authors	   : Rajaram Rabindranath
     *===================================================================*/
    private class Bcast extends Thread
    {
    	String msgToSend = null;
    	public Bcast(String msgToSend)
    	{
    		this.msgToSend = msgToSend;
    	}
    	
    	public void run()
    	{
    		Socket socket = null;
        	PrintWriter writer = null;
        	try
            {
        		// B-mcast -- messages to all devices -- including to self
                for(int i =0;i<remotePorts.length;i++)
                {
                	socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),Integer.parseInt(remotePorts[i]));
                	writer= new PrintWriter(socket.getOutputStream(), true);
                    writer.print(msgToSend);
                    writer.flush();
                    socket.close();
                }
            }
            catch (UnknownHostException e) 
            {
                Log.e(TAG, "ClientTask UnknownHostException");
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "ClientTask socket IOException");
            }
    	}
    }
    
    
    /*====================================================================
     * Function name : update_myMsgCnt
     * Description   : updates the count of the message that have been sent
     * 					helps with setting up message id of the message being 
     * 					sent and also -- updates the sender's component
     * 					in sender's vector clk 
     * Parameters    : void
     * Return		 : void
     *===================================================================*/
	public static synchronized void update_myMsgCnt()
    {
    	synchronized (TAG)
    	{
    		my_msgCnt++;
    		GroupMessengerActivity.update_myVectClk(my_msgCnt);
		}
    	
    }

	/*====================================================================
     * Function name : get_myMsgCnt
     * Description   : This method gets the message count 
     * Parameters    : void
     * Return		 : void
     *===================================================================*/
	public static synchronized String get_myMsgCnt()
    {
    	synchronized (TAG) 
    	{
    		return (Integer.valueOf(my_msgCnt).toString());	
		}
    }
    
    /*====================================================================
     * Class  	   : ClientTask
     * Description : A AsyncTask
     * Class type  : private inner class
     *===================================================================*/
	class ClientTask extends AsyncTask<String, Void, Void> 
    {

        @Override
        protected Void doInBackground(String... args) 
        {
        	Socket socket = null;
        	PrintWriter writer = null;
        	String msgToSend = args[0];
        	try
            {
        		// B-mcast -- messages to all devices
                for(int i =0;i<remotePorts.length;i++)
                {
                	socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),Integer.parseInt(remotePorts[i]));
                	writer= new PrintWriter(socket.getOutputStream(), true);
	                writer.print(msgToSend);
	                writer.flush();
	                socket.close();
                }
	        }
            catch (UnknownHostException e) 
            {
                Log.e(TAG, "ClientTask UnknownHostException");
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }
}

/*============================================================================
 * END OF FILE
 *===========================================================================*/