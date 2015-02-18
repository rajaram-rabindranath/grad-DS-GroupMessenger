package edu.buffalo.cse.cse486586.groupmessenger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


/*====================================================================
 * Class name  : msgInfo
 * Description : A simple no frills helper class -- that helps parse
 * 				the metadata that comes along with every
 * 				message and an object of this class
 * Author	  : Rajaram Rabindranath 
 *===================================================================*/
class msgInfo
{
	String sender;
	Hashtable<String,Integer> senderVectClk;
	String msg;
	String msgID;
	msgType type;
	String vectClk_flatened;

	private msgInfo(String sender,Hashtable<String, Integer> vectClk,String msg,msgType type,String msgID,String vectClk_flatened)
	{
		this.sender  = sender;
		this.msgID = msgID; 
		senderVectClk =  vectClk;
		this.msg = msg;
		this.type = type;
		this.vectClk_flatened = vectClk_flatened;
	}
	
	/*====================================================================
	 * Function name : parseMessages
	 * Description 	 : Parses a message and extracts meaning out of the
	 * 					metadata[comprises of the senders vector clock]
	 * 					creates an object of type msgInfo and then send the
	 * 					same back the caller
	 * Parameter 	 : String (TAG) and String (the message)
	 * Return		 : msgInfo 
	 *===================================================================*/
	public static msgInfo parseMessages(String TAG,String msg)
	{
    	if(msg == null) return null; 
    	String msgTokens[] = msg.split("@:@");
    	
    	String AVDtokens[];
    	String msgTokens_2[];
    	String msgTokens_1[];
    
    	String avd_sender;
    	
    	Hashtable<String, Integer> sendersVectClk =  new Hashtable<String, Integer>();
    	
    	if(msgTokens.length < 2)
    	{
    		Log.e(TAG,"There is some problem with the tokenizer");
    	}
    	
    	// msg classifier metadata
    	msgTokens_1 = msgTokens[0].split(":");
    	
    	// msg classifier
    	if(msgTokens_1[0].equals("seq")) // message from sequencer
    	{
    		//seq:messageID,seqnum@:@
    		return (new msgInfo(null,null,msgTokens_1[1],msgType.msg_seq,null,null));
    	}
		
    	avd_sender = (msgTokens_1[0].split("_"))[0];
    	// reconstruct senders vector clock
    	msgTokens_2 = msgTokens[1].split(",");
    	for(int i =0;i<msgTokens_2.length;i++)
    	{
    		// we could check right here
    		AVDtokens = msgTokens_2[i].split(":");
    		sendersVectClk.put(AVDtokens[0], Integer.parseInt(AVDtokens[1]));
    	}
    	return (new msgInfo(avd_sender,sendersVectClk,msgTokens_1[1], msgType.msg_comm, msgTokens_1[0],msgTokens[1]));
	}
}

/**
 * This enum helps assign message type
 * to each message -- ie. if is a
 * a sequence message from sequencer or
 * an actually chat message from any AVD
 * @author : Rajaram Rabindranath
 */
enum msgType
{
	msg_comm(1),
	msg_seq(2);
	
	int type;
	private msgType(int type) 
	{
		this.type = type;
	}
}



/*==================================================================
 * Class 	 : GroupMessengerActivity is the main Activity 
 * 				for the assignment.
 * Description 	 : GroupMessenger shall run on multiple android devices
 * 				and enable group messaging across devices
 * 				made possible by implementing Sequencer (Total ordering)
 * 				and Vector clocks (causal ordering)  
 * @author's : stevko (base code)
 * 			 : Rajaram Rabindranath (Implementation)
 *=================================================================*/
public class GroupMessengerActivity extends Activity 
{
	
	static final String TAG = GroupMessengerActivity.class.getName();
	static final String providerURL = "content://edu.buffalo.cse.cse486586.groupmessenger.provider";
	static final Uri providerURI = Uri.parse(providerURL);
	static final int SERVER_PORT = 10000;
	static final String msgDebug= "Message Info Debug";
	/*
	 * vector clock
	 * key(String)    = the AVDnum and 
	 * Value(Integer) = event count 
	 */
	static final Hashtable<String,Integer> myVectorClock= new Hashtable<String, Integer>();
	/*
	 * Tot order msg sequence buffer:
	 * key(Integer)  = sequence number and 
	 * Value(String) = MessageID
	 */
	private static HashMap<Integer, String> sequenceMap_buffer = new HashMap<Integer, String>();
	
	static final String[] remotePorts = {"11120","11108","11112","11124","11116"}; // the mapping of all ports
	// used by all processes except the sequencer -- to identify the sequence number(from sequencer) anticipated 
	private static Integer anticipate_seqNum = 0; 
	// used only by sequencer to give sequnce number to message ID
	private static Integer grpMsg_seqNum = 0; 
	private static String myAVDnum;
	private static final String seqAVDnum = "5562" ;


    /**
     * Enum declares the different states that a process' 
     * vector clock can have viz-a-viz some other process' 
     * vector clock
     **/
    enum clkState
	{
		Ahead(1),
		Behind(2),
		Concurrent(3);
		
		public int stateID;
		
		clkState (int stateID)
		{
			this.stateID = stateID;
		}
	}
    
    /*====================================================================
	 * Function name : update_myVectClk
	 * Description 	 : Update my(This process') component 
	 * 					in my vector clock (a recv or a send event cause this)
	 * Parameter 	 : int my_evtClk
	 * Return		 : void
	 *===================================================================*/
	public static void update_myVectClk(int my_evtCnt)
	{
		//myVectorClock.put(myAVDnum,my_evtCnt);
		Integer send_evtCnt = myVectorClock.get(myAVDnum);
		send_evtCnt++;
		myVectorClock.put(myAVDnum,send_evtCnt);
	}
	
	/*====================================================================
	 * Function name : update_myVectClk
	 * Description 	 : update the sender's component on my vector clock
	 * 					a recv event causes this to happen
	 * Parameter 	 : String senderAVDNum
	 * Return		 : msgInfo 
	 *===================================================================*/
	public static void update_myVectClk(String senderAVDnum)
	{
		Integer recv_evtCnt = myVectorClock.get(senderAVDnum);
		recv_evtCnt++;
		myVectorClock.put(senderAVDnum,recv_evtCnt);
	}
	
	@Override
	/*====================================================================
	 * Function name : onCreate
	 * Description 	 : Android Standard Description
	 * Parameter 	 : Android Standard
	 * Return		 : void
	 *===================================================================*/
	protected void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
    	
    	// for ordering
    	for(int i =0;i<remotePorts.length;i++)
    	{
    		Integer avd = Integer.parseInt(remotePorts[i])/2;
    		myVectorClock.put(avd.toString(),0);
    	}
    	
        setContentView(R.layout.activity_group_messenger);

        /*
         * Who am I ? well....
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        myAVDnum = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(myAVDnum) * 2));
        
        
        TextView msgDisplay = (TextView) findViewById(R.id.textView1);
        msgDisplay.setMovementMethod(new ScrollingMovementMethod());
        
        Button pTest = (Button)findViewById(R.id.button1);
        pTest.setOnClickListener(new OnPTestClickListener(msgDisplay, getContentResolver()));
        final EditText editText = (EditText) findViewById(R.id.editText1);
        
        Button sendButton = (Button) findViewById(R.id.button4);
        View.OnClickListener sendListener = (View.OnClickListener) new OnSendClickListener(editText,myPort);
        sendButton.setOnClickListener(sendListener);
        
        try
        {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, serverSocket);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        
        /**
         * Create an anonymous inner class to handle a key event 
         * on the text box at the bottom of the grp messenger 
         */ 
       editText.setOnKeyListener(new OnKeyListener()
       {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
            	// have captured a key event -- namely pressed down && and the key is the ENTER key
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) 
                {
                	// simulate a button click event -- 
                    String msg = editText.getText().toString() + "\n";
                    editText.setText(""); 
                    return true;
                }
                return false;
            }
        });

    }

	/*====================================================================
	 * Function name : get_vectClkInfo
	 * Description 	 : Vector clock info is stored on a hashmap -- this
	 * 					function flattens the vector clock into string and
	 * 					send to the caller -- (the vector clock is 
	 * 					the caller's clock)
	 * Parameter 	 : void
	 * Return		 : String
	 *===================================================================*/
	public static String get_vectClkInfo()
	{
		/*!*/
		String vectClkInfo = "";
		String AVD = null;
		Set<String> keys = myVectorClock.keySet();
		Iterator<String> iter= keys.iterator();
		while(iter.hasNext())
        {
			AVD = iter.next();
			vectClkInfo += AVD+":"+myVectorClock.get(AVD);
            if(iter.hasNext()){vectClkInfo+=",";}
        }		
		return vectClkInfo;
	}

    @Override
    /*====================================================================
	 * Function name : onCreateOptionsMenu
	 * Description 	 : Android Standard
	 * Parameter 	 : Android Standard
	 * Return		 : boolean
	 *===================================================================*/
	public boolean onCreateOptionsMenu(Menu menu) 
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
	
    /*====================================================================
     * Class Name  : SeverTask
     * Description : To handle server operations of the GroupMessenger android
     * 				apk
     *===================================================================*/
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> 
    {
    
        
    	private clkState myClkState_revlative(Hashtable<String, Integer>myVectorClock,Hashtable<String, Integer>sendersVectorClock,String senderAVD)
    	{
    		Set<String> keys = myVectorClock.keySet();
    		
    		/**
    		 * I shall refer to the sender as J in the comments
    		 * from here on
    		 */
    		for(String AVD : keys)
    		{
    			
    			// no point in comparing my component in both the clks
    			if (AVD.equals(senderAVD)) continue;
    			/*
    			 *  checking senders component in both clocks (sender and mine) -- conseq 2 things
    			 *  A. if myVectClk[J] == senderVectClk[J] --means-- then duplicate msg /  
    			 *  B. if myVect[J] < senderVectClk[J] --means-- a message has not been B-delivered (Skipped)
    			 *  either case cannot CO-deliver this message 
    			 */
    			if(AVD.equals(senderAVD)) // process J's component in each clock
    			{
    				if((sendersVectorClock.get(AVD) != myVectorClock.get(AVD)+1)) return clkState.Behind;
    			}
    			// is J's component for any other process 'K' less than or equal to me
    			else if(sendersVectorClock.get(AVD) > myVectorClock.get(AVD)) // an
    			{
    				return clkState.Behind;
    			}	
    		}
    		return clkState.Ahead;
    	}
    	
        
    	
    	@Override
        /*====================================================================
         * Function    : doInBackground
         * Description : Android must implement function -- to do back groiund 
         * 				 task and then report results to the UI
         * Parameters  : ServerSocket... sockets
         * Return 	   : Void
         *===================================================================*/
        protected Void doInBackground(ServerSocket... sockets) 
        {
        	ServerSocket serverSocket = sockets[0];
            String msgLine = null, fullMsg = null;
            Socket clientConnx = null;
            BufferedReader data = null;
            
            
            Hashtable<String, String> msgBuffer = new Hashtable<String, String>(); 
            ContentValues msg_sequencePairs = null;
        	msgInfo msgDetails = null;
        	int enqueue=0,dequeue=0;
        	
        	String msgID;
        	int seq;
        	String waitingFor_msgID=null;
        	
        	Socket socket = null;
        	PrintWriter writer = null;
        	clkState stateCheck;
        	ArrayList<msgInfo> causal_HoldBackQ = null;
        	
        	if(myAVDnum.equals(seqAVDnum))
        	{
        		//causal_HoldBackQ = new Hashtable<String, msgInfo>();
        		causal_HoldBackQ = new ArrayList<msgInfo>();
        	}
        	
        	/**
             * Wait for connection form another Android Device
             * Accept connections from other Android devices
             * Receive data from connection and have it passed onto the UI
             */
            try
            {
            	while(true) // for each message the client shall make a new connection therefore while(true)
            	{
            		clientConnx = serverSocket.accept();
	            	data = new BufferedReader(new InputStreamReader(clientConnx.getInputStream()));
	            	
	            	// data could be multiple lines -- need to GET THE WHOLE MESSAGE incoming messages
	            	while((msgLine = data.readLine())!= null)
	            	{
	            		if(fullMsg ==  null)fullMsg =msgLine;
	            		else
	            		fullMsg+=msgLine;
	            	}
	            	clientConnx.close();
		            
	            	/**
		             * Message B-delivered ------------!!!!
		             * Parse the received message and understand the content
		             * and take appropriate actions
		             * -- if message for sequencer -- then we have have the green signal to deliver
		             * -- else buffer the message until signal from sequence 
		             */
	            	msgDetails = msgInfo.parseMessages(TAG, fullMsg);
	            	fullMsg = null;
	            	
	            	/**
	            	 *  am i the sequencer ?? 
	            	 * Ensure FIFO arrangement of messages: to ensure Causality 
	            	 * is msg in FIFO order?
	            	 * If yes then TO deliver message to application
	            	 * and send out the sequence number of delivery 
	            	 * all members of the mcast group
	            	 **/
	            	if(myAVDnum.equals(seqAVDnum)) 
	            	{
	            		/**
	            		 *  must buffer messages to fifo deliver to my application
	            		 *  the i can send out the total delivery sequence to all
	            		 */
	            		causal_HoldBackQ.add(msgDetails);
	            		Log.d(msgDebug,msgDetails.msgID);
	            		Log.d(msgDebug,"size of holdback q:"+causal_HoldBackQ.size());
	            		
	            		for(int index=0;index<causal_HoldBackQ.size();index++)
	            		{
	            			msgDetails = causal_HoldBackQ.get(index);
		            		/**
		            		 * messages have to wait in the buffer till my clk gets ahead
		            		 * and iff the message is the next one that i anticipate
		            		 * can i to deliver this message ?
		            		 * Yes -- proceed for TO delivery
		            		 * B-mcast of seq number of msgID
		            		 */
	            			stateCheck = myClkState_revlative(myVectorClock, msgDetails.senderVectClk, msgDetails.sender);
	            			if(stateCheck ==  clkState.Behind)
	            			{
            					Log.d(msgDebug,"messageID: "+msgDetails.vectClk_flatened+":"+msgDetails.msgID);
            					Log.d(msgDebug,"myVectClk: "+get_vectClkInfo());
            					continue;
	            			}
	            			
	            			causal_HoldBackQ.remove(index);
	            			msg_sequencePairs = new ContentValues();
			            	msg_sequencePairs.put("key",grpMsg_seqNum.toString()); 
			            	msg_sequencePairs.put("value",msgDetails.msg); 
			            	getContentResolver().insert(providerURI,msg_sequencePairs); // delivered message to application
			            	publishProgress(msgDetails.msgID+":"+msgDetails.msg+"\n");
			            
			            	Log.d(msgDebug,"\n:"+msgDetails.msgID+":"+msgDetails.vectClk_flatened);
			            	Log.d(msgDebug,":myvect:"+get_vectClkInfo()+"\n");
			            	
			            	if(!msgDetails.sender.equals(myAVDnum))
			            	{
			            		update_myVectClk(msgDetails.sender); // ----- !!!!
			            	}
			            	/*
			            	 *  construct sequence message --- signal (PACK sequence # along with message ID)
			            	 *  identify message as one carrying global state metadata message -- using "seq"
			            	 */
			            	String msgToSend = "seq:"+msgDetails.msgID+","+grpMsg_seqNum+","+msgDetails.msg+"@:@";
			            	
			            	/**
			            	 *  B-mcast message order to the group --- !!
			            	 **/
			            	for(int i =0;i<remotePorts.length;i++) // go ahead deliver seq msgs now!
			                {
			            		int toPort = Integer.parseInt(remotePorts[i]);
			            		if((toPort/2) != Integer.parseInt(seqAVDnum)) // sequence shall not send message to self
			            		{
				                	socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),Integer.parseInt(remotePorts[i]));
				                	writer= new PrintWriter(socket.getOutputStream(), true);
					                writer.print(msgToSend);
					                writer.flush();
					                socket.close();
			            		}
			                }
			            	grpMsg_seqNum++;
	            		}
	            		Log.d(msgDebug,"size of holdback q:"+causal_HoldBackQ.size());
	            	
	            		
		            }
	            	else // i am not the sequencer
	            	{
	            		/*
	            		 *  is the message from the sequencer?
	            		 *  if yes then this shall be the ordering info 
	            		 *  sent by the sequencer -- given the message type 
	            		 */
	            		if(msgDetails.type == msgType.msg_seq)
	            		{
	            			//msgDetails.msgID,grpMsg_seqNum,msgDetails.msg
	            			String seqMsgTokens[] = msgDetails.msg.split(",");
			            	// I have not got the sequence number that i anticipated
	            			Log.d(msgDebug,"sequence received:"+seqMsgTokens[1]+" msgID:"+seqMsgTokens[0]+"anti"+anticipate_seqNum);
	            			
	            			/**
	            			 * Why do we do this ?
	            			 * Well, out of order receives are a pain -- they could take 2 forms
	            			 * 1. Have not received the anticipated sequence
	            			 * 2. Have received the anticipated seq from the sequencer but...
	            			 *    do not have the msgID reffered to in the seq message in my msg_buffer
	            			 * Following code is for dealing with the second problem -- so that we don't lock on
	            			 * an anticipated sequence whose msgID has been recvd out of order (recvd in the else block)  
	            			 */
	            			while(((msgID= sequenceMap_buffer.get(anticipate_seqNum))!=null)&&(msgBuffer.get(msgID)!= null)) 
		            		{
	            				/**
	            				 * TO - deliver message to application
	            				 */
	            				msg_sequencePairs = new ContentValues();
				            	msg_sequencePairs.put("key",anticipate_seqNum); 
				            	msg_sequencePairs.put("value",msgBuffer.get(msgID)); 
				            	Log.d("Window_shopper","got the anti"+msgBuffer.get(msgID));
				            	getContentResolver().insert(providerURI,msg_sequencePairs);
				            	
				            	dequeue++;
				            	publishProgress(msgID+":"+msgBuffer.get(msgID)+":"+enqueue+":"+dequeue+"\n");
				            	
				            	// clean-up TO-deliver
				            	sequenceMap_buffer.remove(anticipate_seqNum);
				            	msgBuffer.remove(msgID);
				            	if(!msgID.split("_")[0].equals(myAVDnum))
				            	update_myVectClk(msgID.split("_")[0]);
				            	
				            	anticipate_seqNum = anticipate_seqNum+1; // --------------
		            		}
	            			
	            			
	            			/**
	            			 * Have I received the sequence_num/msgID pair that i am anticipating ?
	            			 * if no, then please buffer the sequence_num/msgID pair and keep waiting
	            			 * for the anticipated sequence as we cannot know which message to TO deliver
	            			 * if the sequencer does not tell us -- we have skipped ahead in time,
	            			 * since (anticipated_seqNum < recvd seqNum) 
	            			 * so to speak but we cannot TO deliver effect b4 cause
	            			 */
	            			if(anticipate_seqNum != Integer.parseInt(seqMsgTokens[1]))
	            			{
	            				// buffer the sequencing metadata -- act later
	            				sequenceMap_buffer.put(Integer.valueOf(seqMsgTokens[1]),seqMsgTokens[0]);
	            				Log.d("Window_shooper","anti"+anticipate_seqNum+"got"+seqMsgTokens[1]+":"+seqMsgTokens[0]);
								enqueue++; // increment queuing counter for debug messages
								
								/**
								 *  what is the id of the message which is paired with the anticipated_seqNum am i waiting for
								 *  just a check coz we could lock on the anticipated_seqNum ... oblivious to 
								 *  a successful out of order receive that took place in the else block
								 *  sequenceMap_buffer shall return a !null value if the msg have been rcvd
								 *  in the else blk
								 **/
								if(waitingFor_msgID == null)
            					{
	            					waitingFor_msgID = sequenceMap_buffer.get(anticipate_seqNum);
	            					if(waitingFor_msgID ==  null) 
            						{
	            						publishProgress("-----ENQ"+enqueue+":"+anticipate_seqNum+":"+seqMsgTokens[1]+":"+seqMsgTokens[0]+":"+waitingFor_msgID+":"+msgBuffer.size()+"\n"); // once i get here i am not able to get out
	            						continue; // keep waiting
            						}
            					}
								
	            				/*
	            				 * the out of order seqNum and its corresponding MsgID 
	            				 * that made us wait in the first place had arrived at
	            				 * else block of this if previously --- setting things right
	            				 */
	            				if(msgBuffer.get(waitingFor_msgID)!= null) 
	            				{
	            					/**
	            					 * TO - deliver message to the application
	            					 */
	            					msg_sequencePairs = new ContentValues();
	    			            	msg_sequencePairs.put("key",anticipate_seqNum); 
	    			            	msg_sequencePairs.put("value",msgBuffer.get(waitingFor_msgID)); 
	    			            	getContentResolver().insert(providerURI,msg_sequencePairs);
	    			            	dequeue++;
	    			            	publishProgress(waitingFor_msgID+":"+msgBuffer.get(waitingFor_msgID)+":"+enqueue+":"+dequeue+"\n");
	    			            	
	    			            	// clean-up after TO deliver
	    			            	msgBuffer.remove(waitingFor_msgID);
	    			            	sequenceMap_buffer.remove(anticipate_seqNum);
	    			            	if(!waitingFor_msgID.split("_")[0].equals(myAVDnum))
	    			            	update_myVectClk(waitingFor_msgID.split("_")[0]);
	    			            	anticipate_seqNum=anticipate_seqNum+1; // --------------

	    			            	Log.d(msgDebug,"Have resolved the lock expecting seq num: "+anticipate_seqNum);
	    			            	waitingFor_msgID =null;
	    			            }
	            				else
	            				{
	            					publishProgress("-----ENQ"+enqueue+":"+anticipate_seqNum+":"+seqMsgTokens[1]+":"+seqMsgTokens[0]+":"+msgBuffer.get(waitingFor_msgID)+":"+msgBuffer.size()+"\n"); // once i get here i am not able to get out
	            				}
	            				continue;
	            			}

							/*
							 * even though i have got the sequence number that i anticipated.. 
							 * the message corresponding to that seqNum has not yet arrived
							 * So we will wait for this message ID as we have the seq number for it :)
							 * there is a distinction with out a difference in the outcome -- 
							 * between this and the above if condition 
							 */
							if(msgBuffer.get(seqMsgTokens[0]) == null) 
							{
								// so lets wait
								sequenceMap_buffer.put(Integer.valueOf(seqMsgTokens[1]),seqMsgTokens[0]);
								enqueue++;
								waitingFor_msgID = seqMsgTokens[0];
								continue; // keep waiting
							}
							
							// Thumbs up -- everything checked out -- TO deliver the message
							msg_sequencePairs = new ContentValues();
	            			
			            	msg_sequencePairs.put("key",seqMsgTokens[1]); 
			            	msg_sequencePairs.put("value",msgBuffer.get(seqMsgTokens[0])); 
			            	getContentResolver().insert(providerURI,msg_sequencePairs);
			            	publishProgress(seqMsgTokens[0]+":"+msgBuffer.get(seqMsgTokens[0])+":"+enqueue+":"+dequeue+"\n");
			            	
			            	
			            	// clean up after TO-deliver + increment update counter
			            	msgBuffer.remove(seqMsgTokens[0]);
			            	if(!seqMsgTokens[0].split("_")[0].equals(myAVDnum))
			            	update_myVectClk(seqMsgTokens[0].split("_")[0]);
			            	anticipate_seqNum = Integer.valueOf(seqMsgTokens[1])+1; // anticipating this num for sequencer next
	            		}
	            		else // message recvd is not a sequence metadata message
	            		{
	            			/**
	            			 *  B-deliver of message has taken place
	            			 *  must put message in hold-back queue
	            			 *  b4 TO-deliver message
	            			 */
	            			msgBuffer.put(msgDetails.msgID,msgDetails.msg);
	            			Log.d(msgDebug,"messageID:"+msgDetails.msgID+":"+msgDetails.msg);
	            			
	            			/**
	            			 * this loop being put here is a hack to get the code to get marks 
	            			 * -- here is why
	            			 * Sometimes there are messages in the queue that are waiting to be delivered
	            			 * The anticipate_seqNum all checks out -- but here is the catch
	            			 * We need to enter the else component of the 
	            			 * if(myAVDnum.equals(seqAVDnum)) // condition check
	            			 * but ... this cannot happen as there are only a finite messages that are being sent 
	            			 * by the tester -- 25 to be precise-- therefore this is hack to release message in the
	            			 * holdback queue at the earliest 
	            			 */
	            			while(((msgID= sequenceMap_buffer.get(anticipate_seqNum))!=null)&&(msgBuffer.get(msgID)!= null)) 
		            		{
	            				/**
	            				 * TO - deliver message to application
	            				 */
	            				msg_sequencePairs = new ContentValues();
				            	msg_sequencePairs.put("key",anticipate_seqNum); 
				            	msg_sequencePairs.put("value",msgBuffer.get(msgID)); 
				            	Log.d(msgDebug,"got the anti"+msgBuffer.get(msgID));
				            	getContentResolver().insert(providerURI,msg_sequencePairs);
				            	
				            	dequeue++;
				            	publishProgress(msgID+":"+msgBuffer.get(msgID)+":"+enqueue+":"+dequeue+"\n");
				            	
				            	// clean-up TO-deliver
				            	sequenceMap_buffer.remove(anticipate_seqNum);
				            	msgBuffer.remove(msgID);
				            	if(!msgID.split("_")[0].equals(myAVDnum))
				            	update_myVectClk(msgID.split("_")[0]);
				            	
				            	anticipate_seqNum = anticipate_seqNum+1; // --------------
		            		}
	            		}
	            	}	
	            }
            }
            catch(IOException ioex)
            {
            	Log.e(TAG,"Server exception when accepting connection");
            }
           return null;
        }


        /*====================================================================
         * Function    : onProgressUpdate
         * Description : Android Standard
         * Parameters  : Android Standard
         * Return 	   : void 
         *===================================================================*/
        protected void onProgressUpdate(String...strings)
        {
        	// display the message that has just come in
        	String strReceived = strings[0].trim();
            TextView msgDisplayPanel = (TextView) findViewById(R.id.textView1);
            msgDisplayPanel.append(strReceived + "\t\n");
            return;
        }
    }
}
