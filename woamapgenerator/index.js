const fs = require("fs");
const argv = require("minimist")(process.argv.slice(2));
const prompt = require('prompt');

// Ask for file name
prompt.start();
prompt.message = "";
prompt.get(['file name'], function (err, result) {
  if (err) { return onErr(err); }

  generateMap(result['file name']);
});

const onErr = (err) => {
  console.log(err);
  return 1;
}

// Get arguments
const {
	width = 10,
	height = 10,
	ore_percentage = 10,
	forest_percentage = 15,
	ore_resource_amount = 100,
	forest_resource_amount = 200,
	gold_percentage = 20,
} = argv;

const heightChecked = height % 2 === 0 ? (height / 2) : ((height + 1) / 2);

const getRandomInt = (min, max) => (
	Math.floor(Math.random() * (max - min + 1)) + min
);

const getRandomType = () => {
	const randomValue = getRandomInt(1, 100);
	if(randomValue <= ore_percentage) return "Ore";
	else if (randomValue > ore_percentage && randomValue <= ore_percentage + forest_percentage) return "Forest";

	return "Ground";
}

const toMapFormat = (x, y) => {
	// Convert normal array positions to map convention
	if(x % 2 === 0) {
		return {
			x: y * 2,
			y: x
		}
	}
	else {
		return {
			x: (y * 2) - 1,
			y: x
		}
	}
}

const generateMap = (filename) => {
	// Set values
	const map = {};

	map.initialPositions = [];
	for(let i = 0; i < 6; i++) {
		let x = getRandomInt(1, width);
		let y = getRandomInt(1, heightChecked);

		while(map.initialPositions.findIndex((value) => value.x === x && value.y === y) >= 0) {
			x = getRandomInt(1, width);
			y = getRandomInt(1, heightChecked);
		}

		map.initialPositions.push(toMapFormat(x, y));
	}

	map.initialResources = {
		gold: 0,
		stone: 10,
		wood: 20,
		food: 50,
	};

	map.mapWidth = width;
	map.mapHeight = height;

	map.tiles = [];
	for(let i = 1; i <= width; i++)
	{
		for(let j = 1; j <= heightChecked; j++)
		{
			const type = getRandomType();
			const tileData = {
				x: -1,
				y: -1,
				resource: type,
			};

			const positions = toMapFormat(i, j);
			tileData.x = positions.x;
			tileData.y = positions.y;

			if(type === "Ore") {
				tileData.resource_amount = ore_resource_amount;
				tileData.gold_percentage = gold_percentage;
			}
			else if(type === "Forest") {
				tileData.resource_amount = forest_resource_amount;
			}

			map.tiles.push(tileData);
		}
	}

	// Write to file
	const dir = "./maps";
	const jsonFile = dir + "/" + filename + ".json";
	const data = JSON.stringify(map, null, 4);

	if (!fs.existsSync(dir)){
	    fs.mkdirSync(dir);
	}

	fs.writeFile(jsonFile, data, function(err) {
	  if (err) {
	  	console.log("There was an error", err);
	  	throw err;
	  }
	  console.log(jsonFile, "generated !");
	});
}

