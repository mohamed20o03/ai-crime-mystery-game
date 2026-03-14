#!/bin/bash
set -e
# Create room
RES=$(curl -s -X POST http://localhost:8080/api/rooms -H "Content-Type: application/json" -d '{"hostName":"Host","language":"Arabic"}')
echo "Room response: $RES"
ROOM=$(echo $RES | grep -o '"roomCode":"[^"]*' | cut -d'"' -f4)

# Join players
curl -s -X POST http://localhost:8080/api/rooms/join -H "Content-Type: application/json" -d "{\"roomCode\":\"$ROOM\",\"playerName\":\"Player2\"}"
curl -s -X POST http://localhost:8080/api/rooms/join -H "Content-Type: application/json" -d "{\"roomCode\":\"$ROOM\",\"playerName\":\"Player3\"}"
curl -s -X POST http://localhost:8080/api/rooms/join -H "Content-Type: application/json" -d "{\"roomCode\":\"$ROOM\",\"playerName\":\"Player4\"}"
echo "Joined P2, P3, P4"

# Start
curl -s "http://localhost:8080/api/rooms/$ROOM/test-start"
echo "Started"
