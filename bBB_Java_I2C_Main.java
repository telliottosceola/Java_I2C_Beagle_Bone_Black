package Main;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public class bBB_Java_I2C_Main {
	
	public static int O_RDWR = 0x00000002;

	public static void main(String[] args) {
		new bBB_Java_I2C_Main();

	}
	
	public bBB_Java_I2C_Main(){
		System.setProperty("jna.nosys", "true");
		
		String fileName = "/dev/i2c-1";
		
		int file = CLibrary.INSTANCE.open(fileName, O_RDWR);
		
		if(file < 0){
			System.out.println("Failed to Open i2c-1 file");
			return;
		}
		
		int address = 0x21;
		int i2c_slave = 0x0703;
		
		int ioctl = CLibrary.INSTANCE.ioctl(file, i2c_slave, address);
		
		if(ioctl < 0){
			System.out.println("ioctl call failed");
			return;
		}
		
		//Set all GPIOs to inputs
		setToInputs(file);
		
		//Send command to read inputs and print in the terminal infinitely.
		byte[] readCommand = {0x09};
		byte[] readData = new byte[1];
		
		while(true){
			if(CLibrary.INSTANCE.write(file, readCommand, 1) != 1){
				break;
			}
			if(CLibrary.INSTANCE.read(file, readData, 1) != 1){
				break;
			}
			int status = readData[0];
			if(status < 0){
				status = status +256;
			}
			//input triggered
			if(status != 255){
				System.out.println(status);
				Server s = new Server(status);
				s.run();
				sendEmail(String.valueOf(status));
				
				//Wait until the input status changes
				while(true){
					if(CLibrary.INSTANCE.write(file, readCommand, 1) != 1){
						break;
					}
					if(CLibrary.INSTANCE.read(file, readData, 1) != 1){
						break;
					}
					int newStatus = readData[0];
					if(newStatus < 0){
						newStatus = newStatus +256;
					}
					if(newStatus != status){
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						break;
					}
				}
			}
		}
		return;
		
		
//		while(true){
//			if(turnOnAllRelays(file)){
//				try {
//					Thread.sleep(100);
//					if(turnOffAllRelays(file)){
//						Thread.sleep(100);
//					}else{
//						break;
//					}
//					
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//					break;
//				}
//			}else{
//				break;
//			}
//		}
//		return;
		
		
	}
	
	public boolean setToInputs(int file){
		byte[] setToInputs = {(byte) 0,(byte) 255};
		
		int writeReturn = CLibrary.INSTANCE.write(file, setToInputs, 2);
		
		if(writeReturn != 2){
			System.out.println("Write failed");
			return false;
		}
		
		byte[] pullInputsUp = {(byte) 9, (byte) 255};
		
		writeReturn = CLibrary.INSTANCE.write(file, pullInputsUp, 2);
		
		if(writeReturn != 2){
			System.out.println("Write failed");
			return false;
		}else{
			return true;
		}
		
	}
	
	public boolean turnOnAllRelays(int file){
		byte[] buffer = {(byte) 10, (byte) 255};
		
		int writeReturn = CLibrary.INSTANCE.write(file, buffer, 2);
		
		if(writeReturn != 2){
			System.out.println("failed to turn all relays on");
			System.out.println("writeReturn = "+writeReturn);
			CLibrary.INSTANCE.close(file);
			return false;
		}else{
			System.out.println("All Relays ON");
			return true;
		}
	}
	
	public boolean turnOffAllRelays(int file){
		byte[] buffer = {(byte) 10,(byte) 0};
		
		int writeReturn = CLibrary.INSTANCE.write(file, buffer, 2);
		
		if(writeReturn != 2){
			System.out.println("failed to turn all relays on");
			System.out.println("writeReturn = "+writeReturn);
			CLibrary.INSTANCE.close(file);
			return false;
		}else{
			System.out.println("All Relays OFF");
			return true;
		}
	}
	
	public interface CLibrary extends Library{
		CLibrary INSTANCE = (CLibrary) Native.loadLibrary((Platform.isWindows() ? "msvcrt" : "c"), CLibrary.class);
		
		public int ioctl(int fd, int cmd, int arg);
		
		public int open(String path, int flags);
		
		public int close(int fd);
		
		public int write(int fd, byte[] buffer, int count);
		
		public int read(int fd, byte[] buffer, int count);
	}

	public void sendEmail(String sendMessage){
		Properties props = new Properties();
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.socketFactory.port", "465");
		props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.port", "465");
 
		Session session = Session.getDefaultInstance(props,
			new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication("ncdtrav@gmail.com","theSpunky32");
				}
			});
 
		try {
 
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress("from@no-spam.com"));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse("4173090849@vtext.com"));
			message.setSubject("Input Tripped");
			message.setText(sendMessage);
 
			Transport.send(message);
 
			System.out.println("Message Sent");
 
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public class Server implements Runnable{
		public static final int defaultPort = 3333;
		private DatagramSocket socket;
		private DatagramPacket packet;
		byte[] dataToSend = new byte[1];
		
		public Server(int data){
			dataToSend[0] = (byte) data;
		}
		
		public void run(){
			try {
				socket = new DatagramSocket();
				InetAddress group = InetAddress.getByName("255.255.255.255");
				packet = new DatagramPacket(dataToSend, dataToSend.length, group, 3333);
				socket.send(packet);
				
				
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("failed to send broadcast");
			}
		}
		
	}
		
}
