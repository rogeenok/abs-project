var express = require('express');
var app = express();
var http = require('http').Server(app);
var io = require('socket.io')(http);
var bodyParser = require('body-parser');

app.use(express.static('static'));
app.use(bodyParser.json());

app.get('/', function(req, res) {
  res.sendFile('index.html');
});

// Start game
app.post('/api/start', function(req, res) {
	console.log("Start game");
	const { players, map } = req.body;
	io.sockets.emit('game_start', { players, map });
	res.end();
});

// New agent
app.post('/api/agent/create', function(req, res) {
	console.log("New agent");
	const { player_id, agent_id, tile } = req.body;
	io.sockets.emit('create_agent', { player_id, agent_id, tile });
	res.end();
});

// Move agent
app.post('/api/agent/move', function(req, res) {
	console.log("Move agent");
	const { agent_id, tile } = req.body;
	io.sockets.emit('move_agent', { agent_id, tile });
	res.end();
});

// Die agent
app.post('/api/agent/die', function(req, res) {
	console.log("Die agent");
	const { agent_id } = req.body;
	io.sockets.emit('die_agent', { agent_id });
	res.end();
});

// Start action
app.post('/api/agent/start', function(req, res) {
	console.log("Start action");
	const { agent_id, type } = req.body;
	io.sockets.emit('start_action', { agent_id, type });
	res.end();
});

// Cancel action
app.post('/api/agent/cancel', function(req, res) {
	console.log("Cancel action");
	const { agent_id } = req.body;
	io.sockets.emit('cancel_action', { agent_id });
	res.end();
});

// Gain resource
app.post('/api/resource/gain', function(req, res) {
	console.log("Gain resource");
	const { player_id, agent_id, resource, amount } = req.body;
	io.sockets.emit('resource_gain', { player_id, agent_id, resource, amount });
	res.end();
});

// Lose resource
app.post('/api/resource/lose', function(req, res) {
	console.log("Lose resource");
	const { player_id, agent_id, resource, amount } = req.body;
	io.sockets.emit('resource_lose', { player_id, agent_id, resource, amount });
	res.end();
});

// Deplete resource
app.post('/api/resource/deplete', function(req, res) {
	console.log("Deplete resource");
	const { tile } = req.body;
	io.sockets.emit('resource_deplete', { tile });
	res.end();
});

// Create building
app.post('/api/building/create', function(req, res) {
	console.log("Create building");
	const { agent_id, type } = req.body;
	io.sockets.emit('create_building', { agent_id, type });
	res.end();
});

// Game finished
app.post('/api/end', function(req, res) {
	console.log("Game finished");
	io.sockets.emit('game_end', {});
	res.end();
});

io.on('connection', function(socket) {
  console.log('game client connected');

  socket.on('disconnect', function(){
    console.log('user disconnected');
  });
});

http.listen(3000, function() {
  console.log('listening on *:3000');
});