# Installation

1. Install version `>= 6.11.0` of Node.js from [the official website](https://nodejs.org/es/).
2. Run `npm install` in the folder where you extracted the code.
3. Run `node index.js` followed by any of the following arguments:
    - `width` (optional, default = 10): size in X axis of the map.
    - `height` (optional, default = 10): size in Y axis of the map.
    - `ore_percentage` (optional, default = 10): probability of a tile being generated with an "ore" resource.
    - `forest_percentage` (optional, default = 15): probability of a tile being generated with a "forest" resource.
    - `ore_resource_amount` (optional, default = 100): amount of resource found in the "ore" tiles.
    - `forest_resource_amount` (optional, default = 200): amount of resource found in the "forest" tiles.
    - `gold_percentage` (optional, default = 20): probability of getting gold when mining an "ore" tile.
- i.e: `node index.js --width=10 --height=13 --gold_percentage=40` 