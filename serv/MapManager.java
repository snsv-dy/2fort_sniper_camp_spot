import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;
// import org.json.simple.ParseException;
import java.io.*;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Vector;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.Random;

class geomClass{
	String uuid = null;
	float width, height, depth;
	Vec3 b0, b1;

	public geomClass(String uuid, float w, float h, float d){
		this.uuid = uuid;
		this.width = w;
		this.height = h;
		this.depth = d;

		b0 = new Vec3(-w/2, -h/2, -d/2);
		b1 = new Vec3(w/2, h/2, d/2);
	}

	public Vec3[] getBounds(float matrix[]){
		Vec3 ret[] = new Vec3[2];
		ret[0] = new Vec3();
		ret[1] = new Vec3();
		ret[0].x = b0.x * matrix[0] + b0.y * matrix[4] + b0.z * matrix[8] + matrix[12];
		ret[0].y = b0.x * matrix[1] + b0.y * matrix[5] + b0.z * matrix[9] + matrix[13];
		ret[0].z = b0.x * matrix[2] + b0.y * matrix[6] + b0.z * matrix[10] + matrix[14];
		// ret[0].x = b0.x * matrix[0] + b0.y * matrix[1] + b0.z * matrix[2] + matrix[3];
		// ret[0].y = b0.x * matrix[4] + b0.y * matrix[5] + b0.z * matrix[6] + matrix[7];
		// ret[0].z = b0.x * matrix[8] + b0.y * matrix[9] + b0.z * matrix[10] + matrix[11];


		ret[1].x = b1.x * matrix[0] + b1.y * matrix[4] + b1.z * matrix[8] + matrix[12];
		ret[1].y = b1.x * matrix[1] + b1.y * matrix[5] + b1.z * matrix[9] + matrix[13];
		ret[1].z = b1.x * matrix[2] + b1.y * matrix[6] + b1.z * matrix[10] + matrix[14];

		return ret;
	}

	public Vec3 getCenter(float matrix[]){
		Vec3 ret = new Vec3();
		ret.x = matrix[12];
		ret.y = matrix[13];
		ret.z = matrix[14];
		return ret;
	}
}

class objClass{
	String uuid = null;
	geomClass geom = null;
	float matrix[];

	Vec3 bounds[];

	public objClass(String uuid, geomClass geom, float matrix[]){
		this.uuid = uuid;
		this.geom = geom;
		this.matrix = matrix;

		this.bounds = this.geom.getBounds(matrix);
		// System.out.printf("b0 %s, b1 %s, ", this.geom.b0.toString(), this.geom.b1.toString());
		// System.out.printf("obj added %s bounds %s ; %s\n", this.uuid, this.bounds[0].toString(), this.bounds[1].toString());
	}

	public Vec3 getCenter(){
		return this.geom.getCenter(this.matrix);
		// Vec3 ret = new Vec3();
		// ret.x = (this.bounds[0].x + this.bounds[1].x) / 2.0f;
		// ret.y = (this.bounds[0].y + this.bounds[1].y) / 2.0f;
		// ret.z = (this.bounds[0].z + this.bounds[1].z) / 2.0f;

		// return ret;
	}

	public float last_dist = 9999;

	public boolean intersect(Ray r){
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

		if(t < 0){
			t = tmax;
			this.last_dist = t;
			if(t < 0) return false;
		}

		this.last_dist = t;

		return true;
	}
}

class SpawnPoint{
	public objClass object;
	public Vec3 center;
	public TeamColor team;

	public SpawnPoint(objClass obj, TeamColor team){
		this.object = obj;
		this.center = obj.getCenter();
		this.team = team;
	}
}

public class MapManager{
	// public static void main(String args[]){
	// 	Vector <Integer> test = new Vector<Integer>();
	// 	test.add(21);
	// 	test.add(1);
	// 	test.add(15);
	// 	test.add(6);

	// 	while(true){
	// 		int i = Math.abs((new Random()).nextInt()) % test.size();
	// 		System.out.println("Getting i " + i);
	// 		break;
	// 	}
	// }

	public boolean ready = false;

	public Vector <geomClass> geometries;
	public Vector <objClass> objects;
	public Vector <SpawnPoint> spawns;

	public Vec3 requestSpawnPosition(TeamColor col){
		if(col == TeamColor.TEAM_SPEC){
			return new Vec3(100.0f, 100.0f, 100.0f);
		}
		
		while(true){
			int i = Math.abs((new Random()).nextInt()) % spawns.size();
			System.out.println("Getting i " + i);
			SpawnPoint t = spawns.get(i);
			if(t.team == col){
				return t.center;
			}
		}
	}

	public MapManager(String filename){
		spawns = new Vector<SpawnPoint>();

		try{

			String content = new String(Files.readAllBytes(Paths.get(filename)));
			JSONParser parser = new JSONParser();
			JSONObject json_root = (JSONObject)parser.parse(content);
				// JSONObject jo = (JSONObject)(new JSONParser().parse(msg_get.str));
			JSONArray geometries = (JSONArray)json_root.get("geometries");
			Iterator <JSONObject> geomIt = geometries.iterator();
			this.geometries = new Vector <geomClass>();

			while(geomIt.hasNext()){
				JSONObject geomt = geomIt.next();

				float x = ((Number)geomt.get("width")).floatValue();
				float y = ((Number)geomt.get("height")).floatValue();
				float z = ((Number)geomt.get("depth")).floatValue();
				String uuid = (String)geomt.get("uuid");

				geomClass t = new geomClass(uuid, x, y, z);
				this.geometries.add(t);
				// System.out.printf("Got geometry: (%02.2f %02.2f %02.2f) %s\n", x, y, z, uuid);
			}

			JSONObject sceneobj = (JSONObject)json_root.get("object");
			JSONArray objects = (JSONArray)sceneobj.get("children");
			Iterator <JSONObject> objIt = objects.iterator();
			this.objects = new Vector <objClass>();
			while(objIt.hasNext()){
				JSONObject objt = objIt.next();
				 String type = (String)objt.get("type");
				 
				 if(!type.equals("Mesh")){
				 	System.out.printf("Type not mesh but: %s\n", type);
				 	continue;
				 }

				 geomClass objg = null;
				 String obj_geom = (String)objt.get("geometry");
				 String uuid = (String)objt.get("uuid");
				 int geom_id = -1;

				 for(int i = 0; i < this.geometries.size(); i++){
				 	geomClass t = this.geometries.get(i);
				 	if(t.uuid.equals(obj_geom)){
				 		geom_id = i;
				 		objg = t;
				 		break;
				 	}
				 }

				 if(objg == null){
				 	System.out.println("Geometry not found for " + uuid);
				 	continue;
				 }

				 JSONArray json_matrix = (JSONArray)objt.get("matrix");
				 float[] matrix = getMatrix(json_matrix);

				 // System.out.printf("Object added (geom %d) ", geom_id, uuid);
				 // System.out.printf(", matrix [%02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f %02.2f ]\n", matrix[0], matrix[1], matrix[2], matrix[3], matrix[4], matrix[5], matrix[6], matrix[7], matrix[8], matrix[9], matrix[10], matrix[11], matrix[12], matrix[13], matrix[14], matrix[15]);

				 objClass obj_object = new objClass(uuid, objg, matrix);
				 this.objects.add(obj_object);

				 String obj_name = (String)objt.get("name");
				 if(Pattern.matches("RedSpawn.*", obj_name)){
				 	SpawnPoint sp = new SpawnPoint(obj_object, TeamColor.TEAM_RED);
				 	spawns.add(sp);
				 	System.out.println("Added red spawn");
				 }else if(Pattern.matches("BlueSpawn.*", obj_name)){
				 	SpawnPoint sp = new SpawnPoint(obj_object, TeamColor.TEAM_BLUE);
				 	spawns.add(sp);
				 	System.out.println("Added blue spawn");
				 }
			}
		}catch(IOException e){
			System.out.println("Failed to read map: " + filename);
			e.printStackTrace();
		}catch(ParseException e){
			System.out.println("Failed to parse json: " + filename);
			e.printStackTrace();
		}
	}

	public float intersect(Ray r){
		float min_dist = 99999;
		for(int i = 0; i < this.objects.size(); i++){
			objClass t = this.objects.get(i);
			if(t.intersect(r)){
				Vec3 tcenter = t.getCenter();
				System.out.printf("Intersected obj %s, dist %02.2f, at \nb0 (%02.2f %02.2f %02.2f)\nb1(%02.2f %02.2f %02.2f)\n", t.uuid, t.last_dist, t.bounds[0].x, t.bounds[0].y, t.bounds[0].z, t.bounds[1].x, t.bounds[1].y, t.bounds[1].z);
				if(t.last_dist < min_dist){
					min_dist = t.last_dist;
				}
			}
		}

		return min_dist;
	}

	public static float[] getMatrix(JSONArray ja){
		float ret[] = new float[16];
		Iterator <Number> it = ja.iterator();
		int i = 0;
		while(it.hasNext()){
			ret[i] = ((Number)it.next()).floatValue();
			i++;
		}

		return ret;
	}
}