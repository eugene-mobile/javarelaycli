package ca.appspace.relaydriver.client;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TCPRelayClient {

	private final String _addr;
	private final int _port;
	private int _timeout = 30000;
	private Socket _relaySocket;
	private DataOutputStream _outputStream;
	private BufferedReader _inputStream;
	private Set<NewDataAvailableCallback> _dataCallbacks = new HashSet<NewDataAvailableCallback>();
	private volatile boolean _shutdownInitiated = false; 
	
	public TCPRelayClient(String addr, int port) {
		this._addr = addr;
		this._port = port;
	}
	
	public static void main(String[] args) throws IOException {
		final long start = System.currentTimeMillis();

		TCPRelayClient client = new TCPRelayClient("216.48.168.68", 1981);
		client.setTimeout(15, TimeUnit.SECONDS);
		client.addDataCallback(new NewDataAvailableCallback() {
			@Override
			public void onNewDataAvailable(String data) {
				System.out.println(((System.currentTimeMillis()-start)/1000)+" New data available: "+data);
			}
		});
		client.connect();

		System.out.println(((System.currentTimeMillis()-start)/1000)+" Sending hello");
		client.sendCommand("Hello!");
		try {	Thread.sleep(1000);		} catch (Exception e) {}
		
		System.out.println(((System.currentTimeMillis()-start)/1000)+" Sending ?");
		client.sendCommand("?");
		try {	Thread.sleep(15000);		} catch (Exception e) {}
		
		System.out.println(((System.currentTimeMillis()-start)/1000)+" Sending s");
		client.sendCommand("s");
		try {	Thread.sleep(15000);		} catch (Exception e) {}
		
		System.out.println(((System.currentTimeMillis()-start)/1000)+" Exiting");
		client.disconnect();
	}

	private void disconnect() {
		_shutdownInitiated = true;
		safeClose(_inputStream);
		safeClose(_outputStream);
		safeClose(_relaySocket);
	}

	private void sendCommand(String string) throws IOException {
		if (_relaySocket==null || _outputStream==null || _relaySocket.isOutputShutdown()) {
			throw new IOException("Socket and/or output stream is closed.");
		}
		_outputStream.writeBytes(string);
		_outputStream.flush();
	}

	private void addDataCallback(NewDataAvailableCallback newDataAvailableCallback) {
		_dataCallbacks.add(newDataAvailableCallback);
	}

	private void connect() throws UnknownHostException, IOException {
		_relaySocket = new Socket(_addr, _port);
		_outputStream = new DataOutputStream(_relaySocket.getOutputStream());
		_inputStream = new BufferedReader(new InputStreamReader(_relaySocket.getInputStream()));
		_shutdownInitiated = false;
		new Thread(new ReadingThread()).start();
	}

	private void setTimeout(int value, TimeUnit timeUnits) {
		if (timeUnits==null) {
			timeUnits = TimeUnit.MILLISECONDS;
		}
		switch (timeUnits) {
			case NANOSECONDS	: { _timeout = value/1000000; break; }
			case MICROSECONDS	: { _timeout = value/1000; break; }
			case MILLISECONDS	: { _timeout = value; break; }
			case SECONDS		: { _timeout = value*1000; break; }
			case MINUTES		: { _timeout = value*1000*60; break; }
			default				: throw new IllegalArgumentException("Timeout can only be configured for up to few minutes");
		}
		
	} 

	class ReadingThread implements Runnable {
		@Override
		public void run() {
			while (!_shutdownInitiated) {
				if (_relaySocket != null && _inputStream != null && !_relaySocket.isInputShutdown()) {
		        	try {
		        		if (_inputStream.ready() && !_dataCallbacks.isEmpty()) {
		        			String line = _inputStream.readLine();
			        		for (NewDataAvailableCallback callback : _dataCallbacks) {
			        			callback.onNewDataAvailable(line);
			        		}
			        		try {
		        				 Thread.sleep(50);
		        			 } catch (InterruptedException e) {}
		        		 }
		        	} catch (IOException e) {
		        		e.printStackTrace();
		        	}
				}
			}
		}
	}

	@Override
	protected void finalize() throws Throwable {
		_shutdownInitiated = true;
		safeClose(_inputStream);
		safeClose(_outputStream);
		safeClose(_relaySocket);
		super.finalize();
	}

	private void safeClose(Closeable cl) {
		if (cl==null) return;
		try {
			cl.close();
		} catch (Throwable e) {}
		
	}
	
	
}
