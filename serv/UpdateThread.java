import java.io.IOException;
import java.util.Vector;

class gameRule{
	public static int respawnTime = 5; // respawn time in seconds
}

class Shoot{
	public TeamColor shooting_team;
	public int shooting_player_id = -1;
	public Ray r;
	public int target_player_id = -1;
	public int damage = 0;
	public boolean headshot = false;
	public boolean killed = false;
	public float distance = 0.0f;

	public Shoot(int id, Ray ray, TeamColor team){
		this.shooting_player_id = id;
		this.r = ray;
		this.shooting_team = team;
	}
}

public class UpdateThread implements Runnable{

	Vector <ClientHandler> clients;
	Vector <Player> players;
	Vector <Player> dead_players;

	Vector <Shoot> shoots;

	MapManager map_manager;

	public UpdateThread(Vector <ClientHandler> client_vec, Vector<Player> player_vec, MapManager mapm){
		this.clients = client_vec;
		this.players = player_vec;
		this.shoots = new Vector <Shoot>();
		this.dead_players = new Vector <Player>();
		this.map_manager = mapm;
	}	

	public void addShoot(int player_id, Ray r, TeamColor team){
		this.shoots.add(new Shoot(player_id, r, team));
	}

	public void shootTest(Shoot sh){
		float min_dist = 100000;
		int closest_object = -1;
		Player closest_playerObj = null;
		System.out.printf("Shooting ray pos: (%02.2f %02.2f %02.2f)\n", sh.r.orig.x, sh.r.orig.y, sh.r.orig.z);

		for(int i = 0; i < this.players.size(); i++){
			Player t = this.players.get(i);
			if(t.id == sh.shooting_player_id || t.team == sh.shooting_team)
				continue;
			
			//
			// headshot implementating in comments
			//

			// boolean shoted = false;
			// if(t.head.intersect(sh.r, t.pos)){
			// 	float dist = Vec3.distance(sh.r.orig, t.pos);
			// 	if(dist < min_dist){
			// 		min_dist = dist;
			// 		closest_object = i;
			// 	}
			// 	sh.headshot = true;
			// 	shoted = true;
			// }else 
			if(t.body.intersect(sh.r, t.pos)){
				// float dist = Vec3.distance(sh.r.orig, t.pos);
				float dist = t.body.last_t;
				if(dist < min_dist){
					min_dist = dist;
					closest_object = i;
					closest_playerObj = t;
					if(t.head.intersect(sh.r, t.headPos)){
					System.out.printf("HEAD SHOT, head pos (%02.2f %02.2f %02.2f)\n", t.headPos.x, t.headPos.y, t.headPos.z);
						sh.headshot = true;
					}
				}
				// shoted = true;
			}

			// if(shoted){
			// 	if(sh.headshot)
			// 		sh.damage = 150;
			// 	else
			// 		sh.health = 50;

			// 	t.health -= sh.damage;
			// 	if(t.health <= 0){
			// 		sh.killed = true;
			// 	}
			// }
		}

		sh.distance = min_dist;

		if(closest_object != -1){
			float ground_collision = this.map_manager.intersect(sh.r);
			if(ground_collision > min_dist && closest_playerObj.alive){
				Player p = closest_playerObj;
				sh.damage = 50;
				sh.target_player_id = p.id;

				System.out.printf("Shot player pos: (%02.2f %02.2f %02.2f)", p.pos.x, p.pos.y, p.pos.z);
				if(sh.headshot){
					sh.damage = 150;
					System.out.printf(", HEADSHOT\n");
				}else{
					System.out.printf("\n");
				}

				p.health -= sh.damage;

				if(p.health <= 0){
					sh.killed = true;
					p.kill();
					dead_players.add(p);
				}
			}else{
				sh.distance = ground_collision;
				System.out.printf("Shot ground, ground distance: %02.3f, player_distance: %02.3f\n", ground_collision, min_dist);
			}
		}
	}
	
	public void forceKill(Player pl){
		pl.kill();
		dead_players.add(pl);
	}

	public void teamChangeSpawn(Player pl){
		dead_players.add(pl);
		pl.deathTimer = 1;
		pl.alive = false;
		pl.special_param = Player.PARAM_TEAM_CHANGE_CONFIRM;
		System.out.println("spawning" + pl.name);
	}

	public void run(){
		boolean running = true;
		long lastTime = (int)System.currentTimeMillis();

		// Main update loop
		while(running){
			// Shooting handling loop
			for(int i = 0; i < this.shoots.size(); i++){
				Shoot t = this.shoots.get(i);
				shootTest(t);

				// if(t.target_player_id == -1){
				// 	// System.out.printf("Handling shoot by: %d, at (%02.2f %02.2f %02.2f)\n", t.shooting_player_id, t.r.dir.x, t.r.dir.y, t.r.dir.z);
				// 	System.out.printf("Removing sh\n");
				// 	shoots.remove(t);
				// 	i--;
				// 	if(shoots.size() <= 0)
				// 		break;
				// }
			}

			//	Death handling loop
			if((System.currentTimeMillis() - lastTime) >= 1000){
				for(int i = 0; i < this.dead_players.size(); i++){
					Player t = this.dead_players.get(i);
					t.deathTimer--;
					System.out.println(t.name + " " + t.deathTimer + "s remaining");
					if(t.deathTimer <= 0){
						Vec3 spawn_pos = this.map_manager.requestSpawnPosition(t.team);
						System.out.printf("Spawning %s, at (%02.2f %02.2f %02.2f)\n", t.name, spawn_pos.x, spawn_pos.y, spawn_pos.z);

						t.respawn(spawn_pos);
						this.dead_players.remove(t);
					}
				}
				lastTime = System.currentTimeMillis();
			}

			// Sending data to clients
			String dataStr = get_player_data_string();
			shoots.clear();

			dataStr = "{\"type\": 1, \"data\": " + dataStr + "}";
			for(int i = 0; i < clients.size(); i++){
				ClientHandler ch = clients.get(i);
				try {ch.sendText(dataStr);} catch(IOException e){System.out.println("Couldn't send text"); e.printStackTrace();}
			}
			try{
				Thread.sleep(1000/30);
			}catch(InterruptedException e){
				System.out.println("Sleep not working");
				e.printStackTrace();
			}
		}

		// pls delete later
		System.out.println("Update thread fucking died. What is this shit?");
	}

	public String get_player_data_string(){
		// preparing shooting array
		String res = "{\"shoots\": ";

		String shoots_ret = "[";
		for(int i = 0; i < shoots.size(); i++){
			Shoot t = shoots.get(i);
			// if(t.target_player_id != -1){
			if(true){
				if(i > 0)
					shoots_ret += ", ";

				if(t.distance == 100000){
					t.distance = 0.0f;
				}

				float shoot_x = t.r.orig.x + t.r.dir.x * t.distance;
				float shoot_y = t.r.orig.y + t.r.dir.y * t.distance;
				float shoot_z = t.r.orig.z + t.r.dir.z * t.distance;


				shoots_ret += 
				"{\"shooting_id\": " + t.shooting_player_id + 
				", \"target_id\": " + t.target_player_id + 
				", \"damage\":" + t.damage + 
				", \"headshot\": " + (t.headshot ? "true" : "false") + 
				", \"killed\": " + (t.killed ? "true" : "false");

				shoots_ret += 
				", \"shoot_pos\": {\"x\": " + shoot_x + ", \"y\": " + shoot_y + ", \"z\": " + shoot_z + "}";

				shoots_ret += "}";
			}
		}
		shoots_ret += "]";

		res += shoots_ret;
		res += ", \"positions\": ";

		String ret = "[";
		try{
			// WSServer.playersLock.lock();
			for(int i = 0; i < players.size(); i++){
				Player t = players.get(i);
				if(i > 0)
					ret += ", ";
				int team = 0;
				if(t.team == TeamColor.TEAM_BLUE)
					team = 1;
				else if(t.team == TeamColor.TEAM_SPEC)
					team = 2;

				ret += 
				"{ \"id\": " + t.id + 
				", \"name\": \"" + t.name + 
				"\",\"pos\": {\"x\":" + t.pos.x + 
				", \"y\": " + t.pos.y + 
				", \"z\": " + t.pos.z + 
				"}, \"dir\": {\"x\":" + t.dir.x + 
				", \"y\": " + t.dir.y + 
				", \"z\": " + t.dir.z + 
				"}, \"alive\": " + (t.alive ? 1 : 0) + 
				", \"deathTimer\":  " + t.deathTimer + 
				", \"health\": " + t.health +
				", \"team\": " + team +
				", \"special_param\": " + t.special_param + "}";

				t.special_param = 0;
			}
			// System.out.printf("updating\n");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			// WSServer.playersLock.unlock();
		}

		ret += "]";

		res += ret;
		res += "}";

		return res;
	}
}