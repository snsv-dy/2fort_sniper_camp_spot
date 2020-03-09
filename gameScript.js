
import * as THREE from "./three.module.js";

import {PointerLockControls} from "./pointer_lock.js"; 

var objects = [];

var textures = [];

var c_width = 650;
var c_height = 450;

var ground = 0.0;

function player_obj(){
	this.mesh = new THREE.Mesh(new THREE.BoxGeometry(0.3, 1.1, 0.1), new THREE.MeshBasicMaterial({map: textures[0]}));

	var gun_attachment = [0.05, 0.05, 0.0];

	this.gunMesh = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.05, 0.5), new THREE.MeshBasicMaterial({color: 0x22ee22}));
	this.gunMesh.position.y = 0.3;
	this.gunMesh.position.z = 0.25;

	this.head = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.3, 0.3), new THREE.MeshBasicMaterial({wireframe: true, color: 0xff0000}));
	this.head.position.y = 1.1 / 2 - 0.3 / 2;

	this.group = new THREE.Group();
	this.group.add(this.mesh);
	this.group.add(this.gunMesh);
	this.group.add(this.head);
	// this.material.map = ;

	this.group.position.y += 1.1 / 2;

	this.id = -1;
	this.name = "";

	this.alive = true;
	this.health = 125;

	this.dispose = function(){
		this.mesh.geometry.dispose();
		this.mesh.material.dispose();

		this.gunMesh.geometry.dispose();
		this.gunMesh.material.dispose();

		this.head.geometry.dispose();
		this.head.material.dispose();
	}

	this.kill = function(){
		this.group.visible = false;
		this.alive = false;
	}

	this.ressurect = function(){
		this.group.visible = true;
		this.alive = true;
	}

	this.setPos = function(pos){
		this.group.position.x = pos.x;
		this.group.position.y = pos.y;
		this.group.position.z = pos.z;
	}

	this.setRotation = function (rot){
		// this.group.rotation.x = rot.x;
		// this.group.rotation.z = rot.z;

		// var zax = [0, 0, 1];
		var ang = Math.atan2(rot.x, rot.z);
		this.group.rotation.y = ang;


	}

	this.testHit = function(uuid){
		if(uuid == this.group.uuid){
			// this.makeInvisible();
			// this.setPos(0, 100.0, 0);
			return true;
		}
		return false;
	}
}

var players_control_sum = -1; // used to check if number of players have changed
var players = [];
var team_stats = [0, 0, 0]; // how many players is in each team
var killfeed = [];

var texLoader = null;
// var global_perspective = getPerspectiveMat(90.0, 0.1, 10.0);
// var global_view = mat4.create();

var fpslockActive = false;
var fpslockValue = 1000/14;

var container;
var camera, scene, renderer, fps, deadCamera;
var sceneReady = false;

var scr_width = 650, scr_height = 450;

// move to player class
var direction = new THREE.Vector3();
var velocity = new THREE.Vector3();
var acc_rate = 0.15;
var max_velo = 1.0;
var friction = 0.60;

var mightAsWellJump = false;
var in_jump = false;
var jump_fall = false;
var jump_peak_vel = 0.5;
var jump_acc = 0.3;

var deltaTime = 1;
// var deltaTime = 0.2;

var aiming = false;

var rayCaster;
//

var TEAM_RED = 0, TEAM_BLUE = 1, TEAM_SPEC = 2;

var socket = null;
var player_id = -1;
var player_name = "";
var request_shoot = false;
var team_change_request = false;
var player_team = TEAM_SPEC;
var player_new_team = TEAM_SPEC;

var player_alive = false;
var player_health = 125;
var player_max_health = 125;
var player_deathTimer = 0;

var player_reloading = false;
var player_reloadTime = 1200;	//ms
var player_reloadVal = player_reloadTime;
var player_reloadBeg = 0;
var player_dims = [0.3, 1.1, 0.1];
var player_head_y = 1.1 - 0.15;

var HUD_DEAD = 0;
var HUD_ALIVE = 1;
var HUD_PL_SELECT = 2;

// var current_hud = HUD_DEAD;
// var current_hud = HUD_ALIVE;
var current_hud = HUD_PL_SELECT;

window.addEventListener('load', function(){
	document.forms[0].connect.addEventListener("click", tryConnect, false);
	document.forms[0].update.addEventListener("click", requestUpdate, false);
	document.forms[0].debug.addEventListener("click", function(){
		// console.log(camera.getWorldDirection());
		// console.log("daed camera: ", deadCamera);
		
		// player_alive = false;
		// console.log("player alive: ", player_alive);
		// make_main_player_dead();

		// hudUpdate();
		// socket.send("{\"type\": 2, \"command_id\": 0}");
		console.log(camera.position);
	}, false);
	// init_game();
});

function destroyObjs(objs){
	for(var i = 0; i < objs.length; i++){
		if(objs[i].type == 'Group'){
			destroyObjs(objs[i].children);
		}else if(objs[i].type == 'Mesh'){
			objs[i].geometry.dispose();
			objs[i].material.dispose();
			if(typeof(objs[i].map) != "undefined"){
				objs[i].map.dispose();
			}
		}
	}
	fps = null;
	players = null;
}

window.addEventListener('beforeunload', function(e){
	// e.preventDefault();
	console.log("before unloading");
	// var objs = scene.children;
	if(scene !== undefined){
		destroyObjs(scene.children);
		renderer.dispose();
		texLoader = null;
		for(var i = 0; i < textures.length; i++){
			textures[i].dispose();
		}
	}
});

var server_addr = "ws://localhost:5001";
function tryConnect(){
	player_name = document.forms[0].name.value;
	server_addr = "ws://" + document.forms[0].ip.value;

	socket = new WebSocket(server_addr);
	socket.onmessage = message_recived;
	console.log("Trying to connect");
	socket.onopen = function(e){
		var request_params = {
			type: MSG_TYPE_WELCOME,
			name: player_name
		};

		console.log("sending params: ", request_params);

		socket.send(JSON.stringify(request_params));
	}
	// socket.onerror	Make this later
}

function requestUpdate(){
	var gb;
	var camera_dir = camera.getWorldDirection(gb);
	var u_str = "{\"type\": 1, \"data\":" +
				" {\"pos\": {\"x\": " + camera.position.x.toFixed(5) +
				 ",\"y\": " + (camera.position.y.toFixed(5) - player_head_y + player_dims[1] / 2) + 
				 ",\"z\": " + camera.position.z.toFixed(5) + "}, "+
		", \"dir\": {\"x\": " + camera_dir.x.toFixed(5) + 
		",\"y\": " + camera_dir.y.toFixed(5) + 
		",\"z\": " + camera_dir.z.toFixed(5) + 
		"}, \"shooting\": " + (request_shoot ? 1 : 0) +
		", \"teamChangeRequest\": " + (team_change_request ? 1 : 0) + 
		", \"team\": " + player_new_team + "}}";

	if(request_shoot){
		// console.log(u_str);
		request_shoot = false;
	}

	socket.send(u_str);
}

var SPECIAL_PARAM_TEAM_CHANGE = 0x1;

var MSG_TYPE_WELCOME = 0;
var MSG_TYPE_UPDATE = 1;
var MSG_TYPE_DIED = 2;

// można usunąć
var deb = false;

var frames_per_seconds = 0;
var last_frame_time = (new Date()).getTime();
var nframes = 0;

function message_recived(e){
	// if(scene == undefined)
	// 	return;

	// console.log("got message: ", e.data);
	var msg = JSON.parse(e.data);

	switch(msg.type){
		case MSG_TYPE_WELCOME:
			if(!sceneReady){
				player_id = msg.id;
				console.log("Oh yeah, we playin. id: ", player_id);
				init_game();
				// sceneReady = true;
				// socket.send(JSON.stringify({type: MSG_TYPE_UPDATE}));
			}else{
				console.log("Attempted to init game twice");
			}
		break;
		case MSG_TYPE_UPDATE:
			// if(!deb){
			// 	deb = true;
			// 	console.log("DEB: ", msg);
			// }

			if(msg.data.shoots.length > 0){
				// console.log("SHOOTO: ", msg.data.shoots);
				for(var i in msg.data.shoots){
					handle_shoot(msg.data.shoots[i]);
				}
			}

			var data = msg.data.positions;
			// console.log("got update", msg.data);
			var player_added = 0;
			var t_control = 0;
			for(var i = 0; i < data.length; i++){
				if(data[i].id == player_id){
					if(player_team != TEAM_SPEC){
						if(player_alive && data[i].alive == 0){
							make_main_player_dead();
						}else if(!player_alive && data[i].alive == 1){
							resurrect_main_player(data[i].pos);
							// if(team_change_request){
							// 	team_change_request = false;
							// 	console.log("team request fulfiled", player_alive, data[i].alive);
							// }
						}
					}

					player_health = data[i].health;
					player_deathTimer = data[i].deathTimer;

					if(!team_change_request)
						player_team = data[i].team;
					// if(player_team == TEAM_SPEC && player_alive){
					// }
					// if(team_change_request && player_team == TEAM_SPEC){
					// 	console.log("spec team");
					// 	// team_change_request = false;
					// 	make_main_player_dead();
					// }

					if(data[i].special_param != 0){
						if((data[i].special_param & SPECIAL_PARAM_TEAM_CHANGE) != 0){
							team_change_request = false;
							console.log("team request fulfiled", player_alive, data[i].alive);
						}
					}

					continue;
				}

				player_added |= update_player(data[i]);
				t_control += data[i].id;
			}

			if(player_added == 0 && players_control_sum != -1 && t_control != players_control_sum){
				console.log("HEAVY OPERATION!");
				// delete some players
				var todelete = [];
				for(var i = 0; i < players.length; i++){
					var t = players[i];
					if(t == undefined)
						continue;

					var found = false;
					for(var j = 0; j < data.length; j++){
						if(t.id == data[j].id){
							found = true;
							break;
						}
					}
					if(!found){
						todelete.push(t);
					}
				}

				for(var i = 0; i < todelete.length; i++){
					remove_disconnected_player(todelete[i]);
				}
			}
			players_control_sum = t_control;
			// console.log("players: ", players);
		break;
		case MSG_TYPE_DIED:
			console.log(msg.id, " has been shoot!");
		break;
	}
}

function killObj(target, shooter, headshot){
	this.age = (new Date()).getTime();
	this.max_age = this.age + 5000;

	this.target_name = target;
	this.shooting_name = shooter;
	this.headshot = headshot;

	// when this is true, delete this object from queue
	this.update = function(){
		return (new Date()).getTime() >= this.max_age;
	}
}

function handle_shoot(sh){
	if(debug_show_shoot){
		console.log("shoot pos: ", sh.shoot_pos);
		debug_shoot_move(sh.shoot_pos);
	}

	if(sh.shooting_id == -1)
		return;

	if(sh.shooting_id == player_id){
		var target = players[sh.target_id];
		if(target != undefined){
			console.log("target not undefined");
			display_hit_damage(sh.damage, [target.group.position.x, target.group.position.y, target.group.position.z]);
		}
	}


	if(sh.killed){
		var target_name, shooting_name;

		if(sh.shooting_id == player_id){
			shooting_name = player_name;
			// console.log("player_name", player_name);
		}else if(players[sh.shooting_id] != undefined){
			shooting_name = players[sh.shooting_id].name;
		}else{
			console.log("shooting player not found ?!?!?!?!?!?!?!?!?");
			shooting_name = "";
		}

		if(sh.target_id == player_id){
			target_name = player_name;
		}else if(players[sh.target_id] != undefined){
			target_name = players[sh.target_id].name;
		}else{
			console.log("target player not found ?!?!?!?!?!?!?!?!?");
			target_name = "";
		}

		killfeed.push(new killObj(target_name, shooting_name, sh.headshot));
	}
}

function remove_disconnected_player(pl){
	scene.remove(pl.group);
	pl.dispose();
	players[pl.id] = undefined;
}

function make_main_player_dead(){
	player_alive = false;

	if(camera.name != "deadCamera"){
		var t = camera;
		camera = deadCamera;
		deadCamera = t;
	}

	current_hud = HUD_DEAD;
	console.log("Player killed");
}

function resurrect_main_player(pos){
	if(player_team != TEAM_SPEC){
		player_alive = true;

		var t = camera;
		camera = deadCamera;
		deadCamera = t;

		camera.position.x = pos.x;
		camera.position.z = pos.z;
		current_hud = HUD_ALIVE;
		console.log("Player came from the dead");
	}
}

function update_player(pl){
	// for(var i = 0; i < players.length; i++){
	// 	if(players[i].id == pl.id){
			
	// 	}
	// }
	// if(!found && sceneReady){
	if(players[pl.id] == undefined){
		// players[pl.id] = 0;
		var t = new player_obj();
		t.id = pl.id;
		t.name = pl.name;
		scene.add(t.group);
		players[pl.id] = t;
		// players.push(t);

		console.log("Created new player: ", pl.name, pl.id);
		return 1;
	}else{
		var t = players[pl.id];
		// console.log("Updating: ", t.name);
		t.setPos(pl.pos);
		t.setRotation(pl.dir);
		// console.log("dir: ", pl.dir);
		if(!pl.alive && t.alive){
			t.kill();
		}else if(pl.alive && !t.alive){
			t.ressurect();
		}
	}

	return 0;
}

function reload_start(){
	player_reloading = true;
	player_reloadBeg = (new Date()).getTime();
}

// tests czy point is in given rect
function isPointInRect(p, rect){
	// console.log("testing ", p, rect, ", res ", (p[0] >= rect[0]) ,
	// 	(p[0] <= (rect[0] + rect[2])) ,
	// 	(p[1] >= rect[1]) ,
	// 	(p[1] <= (rect[1] + rect[3])), " last ", (p[0] >= rect[0]) &&
	// 	(p[0] <= (rect[0] + rect[2])) &&
	// 	(p[1] >= rect[1]) &&
	// 	(p[1] <= (rect[1] + rect[3])));
	return(	(p[0] >= rect[0]) && (p[0] <= (rect[0] + rect[2])) && (p[1] >= rect[1]) && (p[1] <= (rect[1] + rect[3])));

}

var hit_canvas = null;
var hit_canvas_size = 150;
var hit_context = null;
var hit_sprite = null;
var hit_display = false;
var hit_display_time = -1;
var hit_duration = 3000;
var hit_texture = null;

function hit_hide(){
	console.log("hiding sprite");
	hit_display = false;
	hit_sprite.visible = false;
}

function display_hit_damage(value, pos){
	if(hit_sprite != null){
		console.log("sprite not null");//, hit_canvas, hit_sprite);

		hit_context.clearRect(0, 0, hit_canvas_size, hit_canvas_size);
		hit_context.fillText(value, 0, hit_canvas_size / 2);
		hit_texture.needsUpdate = true;

		hit_sprite.position.x = pos[0];
		hit_sprite.position.y = pos[1];
		hit_sprite.position.z = pos[2];

		hit_sprite.visible = true;
		hit_display = true;
		hit_display_time = (new Date()).getTime();
	}
}


var debug_show_shoot = true;
var debug_shoot = null;
function debug_shoot_move(pos){
	if(debug_shoot != null){
		debug_shoot.position.x = pos.x;
		debug_shoot.position.y = pos.y;
		debug_shoot.position.z = pos.z;
	}
}

function init_game(){
	// container = document.createElement('div');
	// container.setAttribute("id", "container");
	// document.body.appendChild(container);

	// getting params from form
	var form = document.forms[0];
	var tw = parseInt(form.custom_width.value);
	var th = parseInt(form.custom_height.value);
	if(typeof(tw) == 'number' && typeof(th) == 'number'){
		scr_width = tw;
		scr_height = th;
	}

	var resize_screen = form.resize_screen.checked;

	container = document.getElementById("container");

	var hud = document.createElement('canvas');
	if(resize_screen){
		hud.style.width = '100%';
		hud.style.height = '100%';
	}

	hud.width = scr_width;
	hud.height = scr_height;
	hud.setAttribute("id", "hud");
	var hc = hud.getContext('2d');

	if(debug_show_shoot)
		debug_shoot = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.1, 0.1), new THREE.MeshBasicMaterial({color: 0x00ff00}));
	debug_shoot.position.x = 999;
	debug_shoot.position.y = 999;
	debug_shoot.position.z = 999;

	hit_canvas = document.createElement("canvas");
	hit_canvas.style.border = '1px solid green';
	hit_canvas.width = hit_canvas_size;
	hit_canvas.height = hit_canvas_size / 2;
	hit_context = hit_canvas.getContext('2d');
	hit_context.fillStyle = 'red';
	hit_context.font = (hit_canvas_size * 0.5) + 'px Arial';
	hit_texture = new THREE.CanvasTexture(hit_canvas);
	hit_sprite = new THREE.Sprite(new THREE.SpriteMaterial({map: hit_texture}));
	// hit_sprite.visible = false;

	container.appendChild(hud);

	// scene = new THREE.Scene();
	var loader = new THREE.ObjectLoader();
	// loader.load("scene.json", function (o){
	loader.load("simple_scene.json", function (o){
		console.log("loaded", o);
		scene = o;
		
		for(var i = 0; i < scene.children.length; i++){
			if(scene.children[i].name == 'deadCamera')
				deadCamera = scene.children[i];
		}

		camera = deadCamera;
		deadCamera = new THREE.PerspectiveCamera(45, scr_width / scr_height, 0.1, 100);
		deadCamera.position.set(0, player_head_y, 0);

		scene.background = new THREE.Color(0xcce0ff);
		scene.fog = new THREE.Fog(0xff0000, 0.1, 1000);

		// var op = new dummy();
		// scene.add(op.mesh);
		// targets.push(op);

		// scene.position.y -= 1.0;
		texLoader = new THREE.TextureLoader();
		textures.push(texLoader.load("snip.png"));
		scene.add(hit_sprite);
		scene.add(debug_shoot);

		sceneReady = true;

		init_rest_of_the_game();
	}, null, null);

	function init_rest_of_the_game(){

		// console.log(camera);
		// spectatorCamera = new THREE.PerspectiveCamera(50, scr_width / scr_height, 0.1, 100);
		// camera.position.set(1000, 50, 1500);
		// scene.add(new THREE.AmbientLight(0x222222));


		rayCaster = new THREE.Raycaster();



		// var light = new THREE.DirectionalLight(0xdfebff, 1);
		// light.position.set(50, 200, 100);
		// light.position.multiplyScalar(1.3);

		// light.castShadow = true;

		// scene.add(light);

		// var geometry = new THREE.BoxGeometry(1, 2, 1);
		// // var material = new THREE.MeshBasicMaterial({color: 0x00ff00});
		// var cube = new THREE.Mesh(geometry, new THREE.MeshPhongMaterial({color: 0xaa1111}));
		// scene.add(cube);

		// var planeg = new THREE.PlaneGeometry();
		// var plane = new THREE.Mesh(new THREE.PlaneGeometry(50, 50), new THREE.MeshPhongMaterial({color: 0xa5a5a5}));
		// plane.doubleSided = true;
		// plane.rotateX(-Math.PI / 2);
		// plane.position.y -= 1;

		// scene.add(plane);
		console.log(camera);

		renderer = new THREE.WebGLRenderer({antialias: true});
		renderer.setPixelRatio(window.devicePixelRatio);
		renderer.setSize(scr_width, scr_height);

		if(resize_screen){
			renderer.domElement.style.width = '100%';
			renderer.domElement.style.height = '100%';
		}	

		container.appendChild(renderer.domElement);

		renderer.outputConfig = THREE.sRGBEncoding;

		renderer.shadowMap.enabled = true;

		// renderer.render(scene, camera);

		// fps = new firstPersonControls(camera, renderer.domElement, function(){
		// 	renderer.render(scene, camera);
		// });


		function onShoot(){
			if(player_alive && !player_reloading){
				reload_start();

				request_shoot = true;
				// rayCaster.setFromCamera(new THREE.Vector2(0, 0), camera);
				// var intersects = rayCaster.intersectObjects(scene.children);
				// for(var i = 0; i < intersects.length; i++){
				// 	var t = intersects[i];
				// 	// for(var j = 0; j < targets.length; i++){
				// 	// 	if(t.object.uuid == targets[j].uuid){
				// 	// 		console.log("player hit!");
				// 	// 		break;
				// 	// 	}
				// 	// }
				// 	for(var k = 0; k < players.length; k++){
				// 		if(players[k].testHit(t.object.uuid)){
				// 			break;
				// 		}
				// 		// if(t.object.uuid == targets[k].uuid){
				// 		// 	// console.log('a', t.object.uuid, targets[k].uuid);
				// 		// 	console.log('player hit!');

				// 		// }
				// 	}
				// }	
				// console.log("inter: ", intersects);
			}
		}

		function onAim(e){
			if(player_alive){
				if(e.button == 2){
					if(aiming){
						camera.fov = 45;
						aiming = false;
					}else{
						camera.fov = 5;
						aiming = true;
					}
					camera.updateProjectionMatrix();
					fps.aimToggle();
				}
			}
		}

		console.log("Fps making");
		fps = new PointerLockControls(deadCamera, hud, onAim, onShoot);
		console.log("Fps made");

		var mrange = document.forms[0].msens_range;
		var mnum = document.forms[0].msens_number;

		mrange.addEventListener('change', (e) => {
			mnum.value = parseInt(mrange.value) / 100;
			fps.updateSens(parseFloat(mnum.value) / 1000);
		});

		mnum.addEventListener('change', (e) =>{
			mrange.value = parseFloat(mnum.value) * 100;
			fps.updateSens(parseFloat(mnum.value) / 1000);
		});



		hud.addEventListener('click', function(e){
			// team choosing
			if(!team_change_request && current_hud == HUD_PL_SELECT){

				var team_button_w = scr_width * 0.20;
				var team_button_h = scr_height * 0.60;
				var team_middle_padding = 50;

				var team_top = (scr_height / 2) - (team_button_h / 2);
				var team_red_left = (scr_width / 2) - team_button_w - team_middle_padding / 2;
				var team_blue_left = (scr_width / 2) + team_middle_padding / 2;
				var team_spec_h = scr_height * 0.1;

				var bounds = hud.getBoundingClientRect();
				var x = ((e.clientX - bounds.left) / bounds.width) * scr_width;
				var y = ((e.clientY - bounds.top) / bounds.height) * scr_height;
				var point = [x, y];

				//testing red
				if(isPointInRect(point, [team_red_left, team_top, team_button_w, team_button_h])){
					console.log("team red");
					player_new_team = TEAM_RED;
					team_change_request = true;
				}else if(isPointInRect(point, [team_blue_left, team_top, team_button_w, team_button_h])){ // testing blue
					console.log("team blue");
					player_new_team = TEAM_BLUE;
					team_change_request = true;
				}else if(isPointInRect(point, [0, scr_height - team_spec_h,scr_width, team_spec_h])){ // testing spectators
					console.log("team gray");
					player_new_team = TEAM_SPEC;
					team_change_request = true;
				}

				if(team_change_request && player_alive){
					player_alive = false;
					current_hud = HUD_DEAD;
					console.log("player not alive");
				}
				// requestUpdate();

			}else if(player_alive){
				console.log("LOCKING", current_hud);
				fps.lock();
			}

		});

		var moveForward = false, moveLeft = false, moveBackward = false, moveRight = false;

	// hud.width = scr_width;
	// hud.height = scr_height;
	// hud.setAttribute("id", "hud");
	// var hc = hud.getContext('2d');
	// hc.beginPath();
	// hc.arc(hud.width / 2, hud.height / 2, 3, 0, 2 * Math.PI, false);
	// hc.strokeStyle = 'lime';
	// hc.stroke();

		var health_h = 40;	// height
		var health_w = 100;	// width
		var health_m = 5;	// margin
		var health_b = 5;

		var reload_w = 40;
		var reload_h = 100;
		var reload_m = 5;
		var reload_b = 5;
		var reload_left = scr_width - reload_b * 2 - reload_m * 2 - reload_w;

		var bars_size = scr_height * 0.1;

		function hudUpdate(){
			hc.clearRect(0, 0, scr_width, scr_height);

			for(var i = 0; i < killfeed.length; i++){
				if(killfeed[i].update()){
					killfeed.splice(i, 1);
					i--;
					continue;
				}

				var t = killfeed[i];
				var killchar = " -> /|\\ ";
				if(t.headshot)
					killchar = " -> O ";
				var str = t.shooting_name + killchar + t.target_name;

				hc.fillStyle = 'black';
				hc.font = 'arial 10pt';
				var measure = hc.measureText(str);
				hc.fillText(str, scr_width - measure.width, i * 13 + 12);
			}

			if(current_hud == HUD_ALIVE){
				// aim
				hc.beginPath();
				hc.arc(hud.width / 2, hud.height / 2, 3, 0, 2 * Math.PI, false);
				hc.strokeStyle = 'lime';
				hc.stroke();

				// health bar
				hc.beginPath();
				hc.rect(health_m, scr_height - (health_m + health_h + health_b * 2), health_w + health_b * 2, health_h + health_b * 2);
				hc.fillStyle = 'gray';
				hc.fill();
				// hc.clearRect(health_b + 5, scr_height - (health_b + 5 + health_h), health_w, health_h);

				var h_fill = (player_health / player_max_health) * health_w;
				hc.beginPath();
				hc.rect(health_m + health_b, scr_height - (health_m + health_h + health_b), h_fill, health_h);
				if(h_fill > health_w * 0.25)
					hc.fillStyle = 'white';
				else
					hc.fillStyle = 'red';
				hc.fill();

				hc.font = '20px Arial';
				hc.fillStyle = 'black';
				hc.fillText(player_health, health_m + health_b + health_w / 2 - 15, scr_height - health_h / 2 + health_b - 8);		


				//reload bar
				hc.beginPath();
				hc.rect(reload_left + reload_m, scr_height - (reload_m + reload_h + reload_b * 2), reload_w + reload_b * 2, reload_h + reload_b * 2);
				hc.fillStyle = 'gray';
				hc.fill();
				// hc.clearRect(reload_b + 5, scr_height - (reload_b + 5 + reload_h), reload_w, reload_h);
				

	// var player_reloading = false;
	// var player_reloadTime = 2000;	//ms
	// var player_reloadVal = 2000;
	// var player_reloadBeg = 0;
				if(player_reloading){
					if(player_reloadVal > player_reloadTime){
						player_reloadVal = player_reloadTime;
						player_reloading = false;
					}else{
						player_reloadVal = (new Date()).getTime() - player_reloadBeg;
					}
				}

				var r_fill = (player_reloadVal / player_reloadTime) * reload_h;
				hc.beginPath();
				hc.rect(reload_left + reload_m + reload_b, scr_height - (reload_m + r_fill + reload_b), reload_w, r_fill);
				if(h_fill > reload_w * 0.25)
					hc.fillStyle = 'white';
				else
					hc.fillStyle = 'red';
				hc.fill();

			}else if(current_hud == HUD_DEAD){
				// drawing bars
				hc.beginPath();
				hc.rect(0, 0, scr_width, bars_size);
				hc.fillStyle = 'rgba(190, 40, 40, 0.7)';
				hc.fill();
				hc.beginPath();
				hc.rect(0, scr_height - bars_size, scr_width, bars_size);
				hc.fill();

				hc.font = '13pt Calibri';
				var str = "Respawn in " + player_deathTimer;
				var measure = hc.measureText(str);
				hc.fillStyle = 'black';
				hc.fillText(str, (scr_width - measure.width) / 2, (bars_size) / 2);
				// hc.fillText(str, (scr_width - measure.width) / 2, measure.height);
			}else if(current_hud == HUD_PL_SELECT){
				var team_button_w = scr_width * 0.20;
				var team_button_h = scr_height * 0.60;
				var team_middle_padding = 50;

				var team_top = (scr_height / 2) - (team_button_h / 2);
				var team_red_left = (scr_width / 2) - team_button_w - team_middle_padding / 2;
				var team_blue_left = (scr_width / 2) + team_middle_padding / 2;
				var team_spec_h = scr_height * 0.1;

				hc.beginPath();
				hc.rect(team_red_left, team_top, team_button_w, team_button_h);
				hc.fillStyle = 'red';
				hc.fill();

				hc.beginPath();
				hc.rect(team_blue_left, team_top, team_button_w, team_button_h);
				hc.fillStyle = 'blue';
				hc.fill();

				hc.beginPath();
				hc.rect(0, scr_height - team_spec_h, scr_width, team_spec_h);
				hc.fillStyle = 'silver';
				hc.fill();

				hc.beginPath();
				hc.font = "13pt Calibri";
				var dispstr = "Choose a team";
				var measure = hc.measureText(dispstr);
				hc.fillStyle = 'black';
				hc.fillText(dispstr, (scr_width - measure.width) / 2, 50);

				dispstr = "Red: " + team_stats[0];
				measure = hc.measureText(dispstr);
				hc.fillText(dispstr, team_red_left, team_top + 13);

				dispstr = "Blue: " + team_stats[1];
				measure = hc.measureText(dispstr);
				hc.fillText(dispstr, team_blue_left, team_top + 13);


				dispstr = "Spectating: " + team_stats[2];
				measure = hc.measureText(dispstr);
				hc.fillText(dispstr, 0, scr_height - team_spec_h + 13);
			}

			hc.beginPath();
			hc.font = '12pt Arial';
			hc.fillText("fps: " + (1000 / frames_per_seconds).toFixed(0), 0, 20);

			if(hit_display && (((new Date()).getTime() - hit_display_time) > hit_duration)){
				hit_hide();
			}
		}

		var refreshInterval = 1000/14;
		// setInterval(requestUpdate, refreshInterval);


		var last_refresh_time = 0;
		function drawExecutor(){
			if(fpslockActive){
				var currentTime = (new Date()).getTime();
				if((currentTime - last_refresh_time) > fpslockValue){
					draw();
					last_refresh_time = currentTime;
				}
			}else{
				draw();
			}
			requestAnimationFrame(drawExecutor);
		}
		drawExecutor();

		function draw(){
			var ttime = (new Date()).getTime();
			requestUpdate();
			hudUpdate();

			frames_per_seconds = ttime - last_frame_time;
			// deltaTime = (last_frame_time - ttime) / 100;
			deltaTime = 0.25;
			last_frame_time = ttime;
			

			// requestUpdate();
			if(player_alive){

				direction.z = Number(moveForward) - Number(moveBackward);
				direction.x = Number(moveRight) - Number(moveLeft);
				direction.normalize();

				if(moveForward || moveBackward) velocity.z -= direction.z * acc_rate;
				if(moveRight || moveLeft) velocity.x -= direction.x * acc_rate;

				// if(Math.abs(velocity.x) >= max_velo) velocity.x = velocity.x >= 0 ? max_velo : -max_velo;

				fps.moveRight( -velocity.x * deltaTime);
				fps.moveForward( -velocity.z * deltaTime);

				if(in_jump){
					if(mightAsWellJump && !jump_fall && velocity.y <= jump_peak_vel){
						velocity.y += jump_acc * deltaTime;
					}else if(jump_fall && velocity.y <= -jump_peak_vel){
						velocity.y = -jump_peak_vel;
					}else{
						jump_fall = true;
						velocity.y -= (jump_acc / 2) * deltaTime;
					}

					camera.position.y += (velocity.y) * deltaTime;
					if(camera.position.y <= player_head_y){
						in_jump = false;
						jump_fall = false;
						camera.position.y = player_head_y;
						velocity.y = 0;
					}
				}else if(mightAsWellJump){
					in_jump = true;
				}

				velocity.x *= friction;
				velocity.z *= friction;
			}

			renderer.render(scene, camera);
		}

		// renderer.domElement.addEventListener('contextmenu', right_click, false);

		// function right_click(e){
		// 	e.preventDefault();
			
		// }

		document.addEventListener('keydown', key_down, false);
		document.addEventListener('keyup', key_up, false);

		// renderer.domElement.addEventListener('mousemove', mouse_handler);
		// renderer.domElement.addEventListener('mouseup', mouse_up);
		// renderer.domElement.addEventListener('mousedown', mouse_down);

		function key_up(e){
			if(player_alive){
				switch ( event.keyCode ) {
					case 38: // up
					case 87: // w
						moveForward = false;
						break;

					case 37: // left
					case 65: // a
						moveLeft = false;
						break;

					case 40: // down
					case 83: // s
						moveBackward = false;
						break;

					case 39: // right
					case 68: // d
						moveRight = false;
						break;

					case 32: // space
						mightAsWellJump = false;
						// if ( canJump === true ) velocity.y += 350;
						// canJump = false;
						break;
				}
			}
		}

		function key_down(e){
			if(player_alive){
				// console.log("what is this shit?");
				switch ( event.keyCode ) {
					case 38: // up
					case 87: // w
						moveForward = true;
						break;

					case 37: // left
					case 65: // a
						moveLeft = true;
						break;

					case 40: // down
					case 83: // s
						moveBackward = true;
						break;

					case 39: // right
					case 68: // d
						moveRight = true;
						break;

					case 32: // space
						mightAsWellJump = true;
						break;
				}
			}
		}
	}
}

// function mouse_up(e){
// 	mouse_dn = false;
// }

// var last_mouse = [0, 0];
// var mouse_dn = false;

// var mouse_sens = 0.01;

// function mouse_down(e){
// 	var box = renderer.domElement.getBoundingClientRect();

// 	last_mouse[0] = e.clientX - box.left;
// 	last_mouse[1] = e.clientY - box.top;

// 	mouse_dn = true;
// }

// function mouse_handler(e){
// 	if(mouse_dn){
// 		var box = renderer.domElement.getBoundingClientRect();
	
// 		var x = e.clientX - box.left;
// 		var y = e.clientY - box.top;

// 		var dx = last_mouse[0] - x;
// 		var dy = last_mouse[1] - y;

// 		camera.rotateY(dx * mouse_sens);
// 		camera.rotateX(dy * mouse_sens);
// 		// console.log(dx);

// 		last_mouse[0] = x;
// 		last_mouse[1] = y;
// 		renderer.render(scene, camera);
// 	}
// }
