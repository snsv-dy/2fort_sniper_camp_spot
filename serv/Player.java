// New face filters on instagram today, this is my favorite one so far. Nice job team.
enum TeamColor{
	TEAM_RED,
	TEAM_BLUE,
	TEAM_SPEC
}

public class Player{
	public static int PARAM_TEAM_CHANGE_CONFIRM = 0x1;
	// public static final int TEAM_RED = 0;
	// public static final int TEAM_BLUE = 1;

	public Vec3 pos, dir;
	// public int team = TEAM_RED;
	public TeamColor team = TeamColor.TEAM_SPEC;
	public int id = -1;
	public String name;

	public Box body, head;// = new Box(this.pos, new Vec3(0.3f, 1.1f, 0.1f));
	public Vec3 feet;
	public Vec3 headPos;

	// public Box head = new Box(new Vec3(0.0f, this.pos.y + this.body.dims.y / 2, 0.0f), new Vec3(0.2f, 0.2f, 0.2f));
	// public Vec3 feet = new Vec3(0.0f, this.pos.y - this.body.dims.y / 2, 0.0f);

	public int health = 125;
	public int max_health = 125;
	public boolean alive = false;
	public int deathTimer = 0;
	public boolean changingTeam = false;
	public int special_param = 0;			// currently used to confirm changing teams

	// dostęp do wątku aktualizowania, żeby zgłaszać strzelanie
	public UpdateThread update_thread = null;

	public Player(Vec3 pos, Vec3 dir, UpdateThread ut){
		this.pos = pos;
		this.dir = dir;
		this.update_thread = ut;

		this.init();
	}

	public Player(UpdateThread ut){
		this.pos = new Vec3(0.0f, 0.0f, 0.0f);
		this.dir = new Vec3(0.0f, 0.0f, 1.0f);
		this.update_thread = ut;

		this.init();
	}

	private void init(){
		this.pos.y = 1.1f / 2.0f;
		this.body = new Box(this.pos, new Vec3(0.3f, 1.1f, 0.1f));
		this.head = new Box(new Vec3(0.0f, 0.95f, 0.0f), new Vec3(0.3f, 0.3f, 0.3f));
		// this.head.center.y -= this.head.dims.y / 2.0f;
		this.feet = new Vec3(0.0f, this.pos.y - this.body.dims.y / 2, 0.0f);

		this.headPos = new Vec3(this.head.center);
	}

	public void update_pos(Vec3 pos){ this.update_pos(pos.x, pos.y, pos.z); }

	public void update_pos(float x, float y, float z){
		this.pos.x = x;
		this.pos.y = y;
		this.pos.z = z;

		this.headPos.x = x;
		this.headPos.y = y + this.head.center.y - this.body.dims.y / 2.0f;
		// this.headPos.y = y;
		this.headPos.z = z;
	}

	public void update_dir(float x, float y, float z){
		this.dir.x = x;
		this.dir.y = y;
		this.dir.z = z;
	}

	public void shoot(){
		System.out.printf("Bamboo shooting: pos (%02.2f %02.2f %02.2f), dir (%02.2f %02.2f %02.2f)\n", this.headPos.x, this.headPos.y, this.headPos.z, this.dir.x, this.dir.y, this.dir.z);
		this.update_thread.addShoot(this.id, new Ray(this.headPos, this.dir), this.team);
	}

	public void kill(){
		this.health = 0;
		this.alive = false;
		this.deathTimer = gameRule.respawnTime;
		
		this.update_pos(1000.0f, 1000.0f, 1000.0f);
	}

	public void respawn(Vec3 point){
		this.alive = true;
		this.health = this.max_health;

		this.update_pos(point.x, point.y + this.body.dims.y / 2, point.z);
		this.changingTeam = false;
	}

	public void changeTeam(int teamid){
		if(!changingTeam){
			switch(teamid){
				case 0:
					this.team = TeamColor.TEAM_RED;
					System.out.printf("%s choose red team\n", this.name);
					break;
				case 1:
					this.team = TeamColor.TEAM_BLUE;
					System.out.printf("%s choose blue team\n", this.name);
					break;
				case 2:
					this.team = TeamColor.TEAM_SPEC;
					System.out.printf("%s choose spectators team\n", this.name);
					break;
			}

			this.update_thread.teamChangeSpawn(this);
			changingTeam = true;
		}
	}

	// sprawdza czy jakikolwiek gracz został trafiony
	public static int testHit(Player pl){
		Ray r = new Ray(pl.pos, pl.dir);
		System.out.printf("Ray: pos (%02.2f %02.2f %02.2f), dir: (%02.2f %02.2f %02.2f)\n", pl.pos.x, pl.pos.y, pl.pos.z, pl.dir.x, pl.dir.y, pl.dir.z);
		// offset is pl.pos
		float min_dist = 100000;
		int closest_object = -1;
		for(int i = 0; i < WSServer.players.size(); i++){
			Player t = WSServer.players.get(i);
			if(t.id == pl.id)
				continue;

			boolean hit = t.body.intersect(r, t.pos);
			if(hit){
				float dist = Vec3.distance(pl.pos, t.pos);
				if(dist < min_dist){
					min_dist = dist;
					closest_object = i;
				}
			}
		}

		if(closest_object != -1){
			System.out.printf("%s shoot!\n", WSServer.players.get(closest_object).name);
			String msg = "{\"type\": 2, \"id\": " + WSServer.players.get(closest_object).id + "}";
			WSServer.send_to_all(msg);
		}
		return 0;
	}
}


class Box{
	public Vec3 center;
	public Vec3 dims;

	public Box(Vec3 center, Vec3 dims){
		this.center = center;
		this.dims = dims;
	}

	public Box(){
		center = new Vec3();
		dims = new Vec3();
	}

	public float last_t = -1;

	public boolean intersect(Ray r, Vec3 offset){
		Vec3 bounds[] = new Vec3[2];
		bounds[0] = new Vec3(offset.x - (dims.x / 2), offset.y - (dims.y / 2), offset.z - (dims.z / 2));
		bounds[1] = new Vec3(offset.x + (dims.x / 2), offset.y + (dims.y / 2), offset.z + (dims.z / 2));
		// bounds[0] = b0;
		// bounds[1] = b1;

		float tmin, tmax, tymin, tymax, tzmin, tzmax;
		tmin = (bounds[r.sign[0]].x - r.orig.x) * r.invdir.x;
		tmax = (bounds[1 - r.sign[0]].x - r.orig.x) * r.invdir.x;
		tymin = (bounds[r.sign[1]].y - r.orig.y) * r.invdir.y;
		tymax = (bounds[1 - r.sign[1]].y - r.orig.y) * r.invdir.y;

		if((tmin > tymax) || (tymin > tmax))
			return false;

		if(tymin > tmin)
			tmin = tymin;
		if(tymax < tmax)
			tmax = tymax;


		tzmin = (bounds[r.sign[2]].z - r.orig.z) * r.invdir.z;
		tzmax = (bounds[1 - r.sign[2]].z - r.orig.z) * r.invdir.z;

		if((tmin > tzmax) || (tzmin > tmax))
			return false;

		if(tzmin > tmin)
			tmin = tzmin;
		if(tzmax < tmax)
			tmax = tzmax;

		float t = tmin;
		last_t = t;

		if(t < 0){
			t = tmax;
			last_t = t;
			if(t < 0) return false;
		}

		return true;
	}
}

class Ray{
	public Vec3 orig, dir, invdir;
	public int sign[] = new int[3];
	public Ray(Vec3 orig, Vec3 dir){
		this.orig = orig;
		this.dir = dir;

		this.invdir = new Vec3();
		this.invdir.x = 1.0f / dir.x;
		this.invdir.y = 1.0f / dir.y;
		this.invdir.z = 1.0f / dir.z;		

		this.sign[0] = (this.invdir.x < 0 ? 1 : 0);
		this.sign[1] = (this.invdir.y < 0 ? 1 : 0);
		this.sign[2] = (this.invdir.z < 0 ? 1 : 0);
	}
}


class Vec3{
	public float x, y, z;
	public Vec3(){
		this.x = 0.0f; this.y = 0.0f; this.z = 0.0f;	
	}

	public Vec3(Vec3 copy){
		this.x = copy.x; this.y = copy.y; this.z = copy.z;	
	}

	public Vec3(float x, float y, float z){
		this.x = x; this.y = y; this.z = z;
	}

	public void normalize(){
		float len = (float)Math.sqrt(x*x + y*y + z*z);
		this.x /= len;
		this.y /= len;
		this.z /= len;
	}

	public static float distance(Vec3 a, Vec3 b){
		return (float)Math.sqrt( Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2) + Math.pow(b.z - a.z, 2));
	}

	public String toString(){
		return String.format("(%02.2f %02.2f %02.2f)", this.x, this.y, this.z);
	}
}
