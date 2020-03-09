import java.util.concurrent.locks.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.Base64;
import java.util.regex.*;
import java.util.Scanner;
import java.security.MessageDigest;
import java.util.Random;
import java.util.Vector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

class recvRes{
	public int error;
	public String str;

	public recvRes(int e, String s){
		this.error = e;
		this.str = s;
	}

	public recvRes(){}
}

class ClientHandler implements Runnable{

	public Socket client;
	public int id = -1;

	public static final int OP_EXIT = 0x8;
	public static final int OP_PING = 0x9;
	public static final int OP_PONG = 0xA;


	InputStream is = null;
	OutputStream os = null;

	Random rand = new Random();
	// public Vector<Player> players;
	public Player playerObj = null;
	public UpdateThread update_thread = null;
	private boolean ready = false;

	public void sendText(String str) throws IOException{
		try{
			if(os != null && ready){
				int opcode = 0x81;
				os.write((byte)opcode);

				int len = 0;
				if(str.length() > 125){
					os.write((byte)(0xFE));
					byte[] bytes = ByteBuffer.allocate(4).putInt(str.length()).array();
					os.write(bytes[2]);
					os.write(bytes[3]);
					// System.out.println("SHIT!");
				}else{
					os.write((byte)(str.length() | 0x80));
					// System.out.printf("Good anakin, good: %d\n", (byte)(str.length() | 0x80));
				}

				byte[] key = new byte[4];
				rand.nextBytes(key);
				os.write(key);

				byte[] encoded_msg = new byte[str.length()];
				for(int i = 0; i < str.length(); i++){
					encoded_msg[i] = (byte)((byte)str.charAt(i) ^ key[i & 3]);
				}

				os.write(encoded_msg);

				byte[] decoded_msg = new byte[str.length()];
				for(int i = 0; i < str.length(); i++){
					decoded_msg[i] = (byte)(encoded_msg[i] ^ (key[i & 0x3]));
				}

				// System.out.println("Sending: " + new String(decoded_msg));

			}else{
				System.out.println("Couldn't send messsage: output stream is null.");
			}
		}catch(IOException e){
			System.out.println(e.toString());
			throw(e);
		}
	}

	public void sendEnd() throws IOException{
		int opcode = 0x80 | OP_EXIT;
		os.write((byte)opcode);

		os.write((byte)0x80);

		byte[] key = new byte[4];
		rand.nextBytes(key);
		os.write(key);

		System.out.println("Ending connection");
	}

	public recvRes RecvText() throws IOException{
		int opcode = is.read();
		if((opcode & 0xF) == OP_EXIT){
			sendEnd();
			return new recvRes(1, "");
		}

		int len_test = is.read();

		long len = 0;
		if((len_test & 0x7F) >= 0 && (len_test & 0x7F) <= 125){
			len = (len_test & 0x7F);
		}else if(len_test == 0xFE){
			byte[] s_len = new byte[2];
			is.read(s_len, 0, 2);
			len = ByteBuffer.wrap(s_len).getShort();
		}else if(len_test == 0xFF){
			byte[] s_len = new byte[8];
			is.read(s_len);
			len = ByteBuffer.wrap(s_len).getLong();
			// System.out.println("len3");
		}

		// System.out.printf("Message length: %d\nOpcode: %x(%d)\nLen: %x(%d)\n", len, opcode, opcode, len_test, len_test);

		byte[] key = new byte[4];
		is.read(key);

		byte[] msg_data = new byte[(int)len];
		is.read(msg_data);


		for(int i = 0; i < len; i++){
			msg_data[i] = (byte)(msg_data[i] ^ (key[i & 0x3]));
		}
		// System.out.println("Recived message: " + new String(msg_data));

		return new recvRes(0, new String(msg_data));
	}

	public ClientHandler(Socket client, UpdateThread updateth){
		this.client = client;
		this.update_thread = updateth;
		// this.players = player_vec;
	}

	public String get_players_data(){
		// System.out.println("Players size: " + WSServer.players.size());
		String ret = "[";
		try{
			// WSServer.playersLock.lock();
			for(int i = 0; i < WSServer.players.size(); i++){
				Player t = WSServer.players.get(i);
				if(t.id == this.id)
					continue;
				if(i > 0)
					ret += ", ";

				ret += "{ \"id\": " + t.id + ", \"name\": \"" + t.name + "\",\"pos\": {\"x\":" + t.pos.x + ", \"y\": " + t.pos.y + ", \"z\": " + t.pos.z + "}, \"dir\": {\"x\":" + t.dir.x + ", \"y\": " + t.dir.y + ", \"z\": " + t.dir.z + "}, \"alive\": 1}";
			}
			// System.out.printf("updating\n");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			// WSServer.playersLock.unlock();
		}

		ret += "]";
		return ret;
	}

	public static final int COMMAND_KILLME = 0;

	public void run(){
		try{
			is = client.getInputStream();
			os = client.getOutputStream();
		}catch(Exception e){

			System.out.printf("Couldn't get io streams: %s\n", e.getMessage());
			return;
		}
		Scanner s = null;
		try{
			 s = new Scanner(is, "UTF-8");

			String data = s.useDelimiter("\\r\\n\\r\\n").next();
			Matcher get = Pattern.compile("^GET").matcher(data);
			if(get.find()){
				Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
				match.find();
				byte[] response = (
					"HTTP/1.1 101 Switching Protocols\r\n"
					+ "Connection: Upgrade\r\n"
					+ "Upgrade: websocket\r\n"
					+ "Sec-WebSocket-Accept: "
					+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
					+ "\r\n\r\n").getBytes("UTF-8");
				os.write(response, 0, response.length);
				ready = true;
			}
		}catch(Exception e){

			System.out.printf("WS handshake error: %s\n", e.getMessage());
			return;
		}

		while(!client.isClosed()){
			recvRes msg_get = null;
			try{
				msg_get = RecvText();
				if(msg_get.error == 1)
					break;

			// System.out.println("msg got: " + msg_get.str);

			JSONObject jo = (JSONObject)(new JSONParser().parse(msg_get.str));
			int type = (int)((long)jo.get("type"));
			switch(type){
				case 0:	// give id
					{
						String ret = "{\"type\": 0, \"id\": " + this.id + "}";
						Player tplayer = new Player(this.update_thread);
						tplayer.id = this.id;
						tplayer.name = (String)jo.get("name");
						WSServer.players.add(tplayer);
						this.playerObj = tplayer;

						System.out.printf("Player `%s` connected, id: %d\n", tplayer.name, tplayer.id);
						boolean id_sent = false;
						while(!id_sent){
							try{
								sendText(ret);	
								id_sent = true;
							}catch(Exception e){
								System.out.printf("Id sending error: %s\n", e.getMessage());
							}
						}
					}
				break;
				case 1: // update pos
					{
						// String ret = "{\"type\": 1, \"data\": " + get_players_data() + "}";
						// sendText(ret);

						JSONObject pldata = (JSONObject)jo.get("data");
						if(this.playerObj.alive){

							JSONObject pos = (JSONObject)pldata.get("pos");
							float x = ((Number)pos.get("x")).floatValue();
							float y = ((Number)pos.get("y")).floatValue();
							float z = ((Number)pos.get("z")).floatValue();

							this.playerObj.update_pos(x, y, z);

							JSONObject dir = (JSONObject)pldata.get("dir");
							x = ((Number)dir.get("x")).floatValue();
							y = ((Number)dir.get("y")).floatValue();
							z = ((Number)dir.get("z")).floatValue();
							this.playerObj.update_dir(x, y, z);

							int shoot = (int)((long)pldata.get("shooting"));
							if(shoot > 0){
								System.out.println("Shoot!");
								this.playerObj.shoot();
								// Player.testHit(this.playerObj);
							}
						}
						int teamChange = ((Number)pldata.get("teamChangeRequest")).intValue();
						if(teamChange != 0 && !this.playerObj.changingTeam){
							int team = ((Number)pldata.get("team")).intValue();
							this.playerObj.changeTeam(team);
						}
					}
				break;
				case 2:
					{	
						int command_id = ((Number)jo.get("command_id")).intValue();
						switch(command_id){
							case COMMAND_KILLME:
								this.update_thread.forceKill(this.playerObj);
								// this.playerObj.kill();
								break;
						}
					}
				break;
			}

			}catch(ParseException e){
				System.out.printf("Invalid json: %s, error: %s\n", ((msg_get == null) ? "" : msg_get.str), e.getMessage());
			}catch(IOException e){
				System.out.printf("IO operation failed: %s\n", e.getMessage());
			}
		}

		WSServer.players.remove(this.playerObj);
		WSServer.client_objects.remove(this);
		System.out.printf("Player %d disconnected\n", this.id);
		try{
			if(s != null)
				s.close();
			client.close();
		}catch(Exception e){
			System.out.printf("Couldn't close socket or scanner: %s\n", e.getMessage());
		}
	}
}

class BotPlayer{
	Player playerObj;

	public Vector<Vec3> path;
	int cur_path_point = 0;
	float min_point_distance = 0.5f;
	float speed = 0.1f;
	boolean moving = false;

	public BotPlayer(UpdateThread ut){
		playerObj = new Player(ut);
		playerObj.pos.y = WSServer.ground;

		this.path = new Vector<Vec3>();

		path.add(new Vec3(-5.0f, 0.0f, 0.0f));
		path.add(new Vec3(5.0f, 0.0f, 0.0f));
		path.add(new Vec3(5.0f, 0.0f, 5.0f));
		path.add(new Vec3(-5.0f, 0.0f, 5.0f));
	}

	public void update(){
		if(moving){
		Vec3 cpp = this.path.get(cur_path_point);
		float dist = (float)Math.sqrt( Math.pow(this.playerObj.pos.x - cpp.x, 2) + Math.pow(this.playerObj.pos.z - cpp.z, 2) );
		// System.out.println("Dist: " + dist + ", cpp: " + cur_path_point);
		// System.out.printf("Dist: %02.2f, cpp: %d, pos: (%02.2f %02.2f %02.2f)", dist, cur_path_point, this.playerObj.pos.x, this.playerObj.pos.y, this.playerObj.pos.z);
		// System.out.printf(", point: (%02.2f %02.2f %02.2f)\n", cpp.x, cpp.y, cpp.z);
		if(dist < this.min_point_distance){
			this.cur_path_point++;
			if(this.cur_path_point >= this.path.size()){
				this.cur_path_point = 0;
			}
		}

		cpp = this.path.get(cur_path_point);
		this.playerObj.dir.x = cpp.x - this.playerObj.pos.x;
		this.playerObj.dir.z = cpp.z - this.playerObj.pos.z;

		this.playerObj.dir.normalize();

		// System.out.printf("dir: (%02.2f %02.2f %02.2f)\n", this.playerObj.dir.x, this.playerObj.dir.y, this.playerObj.dir.z);

		this.playerObj.pos.x += this.playerObj.dir.x * this.speed;
		this.playerObj.pos.z += this.playerObj.dir.z * this.speed;
		}
	}
}

class BotThread implements Runnable{
	Vector <BotPlayer> bots;
	// public static Vector <Player> players = Vector <Player>();

	public int botid = 5000;
	public int time = 0;

	public UpdateThread update_thread;

	BotThread(Vector <Player> player_vec, UpdateThread ut){
		// players = player_vec;
		bots = new Vector <BotPlayer>();
		this.update_thread = ut;
	}

	public void create_bot(Vec3 pos, int id){
		BotPlayer bot = new BotPlayer(this.update_thread);
		bot.playerObj.id = id;
		bot.playerObj.name = "Bot Naki";
		bot.playerObj.update_pos(pos.x, pos.y, pos.z);
		// bot.playerObj.pos.y += bot.playerObj.head.center.y;

		bot.playerObj.update_dir(-pos.x, -pos.y, -pos.z);
		bot.playerObj.dir.normalize();
		bot.playerObj.alive = true;
		bot.playerObj.team = TeamColor.TEAM_BLUE;

		WSServer.players.add(bot.playerObj);
		bots.add(bot);
	}

	public void create_bot(int id){
		this.create_bot(new Vec3(0.0f, 0.0f, 0.0f), id);
	}

	public void run(){
		try{
			while(true){
				Thread.sleep(50);
				BotPlayer t = bots.get(0);
				t.update();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

public class WSServer{
	ServerSocket server;
	int port = 5001;
	int clients = 0;

	public static float ground = 1.0f;
	public static Vector<Player> players = new Vector<Player>();
	public static ReentrantLock playersLock = new ReentrantLock();

	BotThread bot_th = null;
	UpdateThread update_thread = null;
	MapManager map_manager = null;

	int server_capacity = 200;
	boolean[] free_ids;

	public static Vector<ClientHandler> client_objects = new Vector<ClientHandler>();

	public static void send_to_all(String msg){
		for(int i = 0; i < WSServer.client_objects.size(); i++){
			try{
				ClientHandler ch = WSServer.client_objects.get(i);
				ch.sendText(msg);
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}

	public int find_first_free_id(){
		for(int i = 0; i < server_capacity; i++){
			if(this.free_ids[i] == false){
				this.free_ids[i] = true;
				return i;
			}
		}
		return -1;
	}

	// todo
	// wall shooting checking
	// reloading delay (can be in client)
	public WSServer(){
		this.free_ids = new boolean[server_capacity];
		for(int i = 0; i < server_capacity; i++)
			this.free_ids[i] = false;

		this.map_manager = new MapManager("../simple_scene.json");
		this.update_thread = new UpdateThread(WSServer.client_objects, WSServer.players, this.map_manager);
		bot_th = new BotThread(this.players, this.update_thread);
		bot_th.create_bot(new Vec3(-14.0f, 0.55f, -8.0f), find_first_free_id());
		bot_th.create_bot(new Vec3(-14.0f, 0.55f, -4.0f), find_first_free_id());
		bot_th.create_bot(new Vec3(-14.0f, 0.55f, 0.0f), find_first_free_id());
		bot_th.create_bot(new Vec3(-14.0f, 0.55f, 4.0f), find_first_free_id());
		bot_th.create_bot(new Vec3(-14.0f, 0.55f, 8.0f), find_first_free_id());
		(new Thread(bot_th)).start();
		(new Thread(this.update_thread)).start();

		try{
			server = new ServerSocket(port);
			while(true){
				Socket client = server.accept();
				
				ClientHandler handler = new ClientHandler(client, this.update_thread);
				int id = find_first_free_id();
				if(id == -1){
					// server is full handling
				}

				handler.id = id;
				WSServer.client_objects.add(handler);

				(new Thread(handler)).start();
			}
		}catch(IOException e){
			System.out.println(e.toString());
		}
	}

	public static void main(String args[]){
		// Vector<Integer> t = new Vector<Integer>();
		// t.add(5);
		// for(int i = 0; i < t.size(); i++)
		// 	System.out.printf("%d, ", t.get(i));
		// System.out.println("");

		// modVector(t);

		// for(int i = 0; i < t.size(); i++)
		// 	System.out.printf("%d, ", t.get(i));
		// System.out.println("");
		WSServer ws = new WSServer();
		// WSServer list = new WSServer();

		// try{
		// 	String pstr = "{\"type\": 1, \"data\": {\"pos\": {\"x\": " + 0.01f + ",\"y\": " + 0.3f + ",\"z\": " + 1.0f + "}, "+
		// ", \"aim\": {\"x\": " + 3 + ",\"y\": " + 2 + ",\"z\": " + 1 + "}}}";
		// 	JSONObject jo = (JSONObject)(new JSONParser().parse(pstr));
		// 	int type = (int)((long)jo.get("type"));
		// 	JSONObject data = (JSONObject)jo.get("data");
		// 	JSONObject pos = (JSONObject)data.get("pos");
		// 	float x = (float)((double)pos.get("x"));
		// 	float y = (float)((double)pos.get("y"));
		// 	float z = (float)((double)pos.get("z"));

		// 	System.out.printf("pos: %.2f %.2f %.2f ", x, y, z);
		// }catch(Exception e){
		// 	e.printStackTrace();
		// }
	}
}