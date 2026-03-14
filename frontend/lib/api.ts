const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ApiResponse<T> {
  data?: T;
  error?: string;
}

export interface CreateRoomResponse {
  roomCode: string;
  playerId: string;
  playerName: string;
  isHost: boolean;
}

export interface JoinRoomResponse {
  roomCode: string;
  playerId: string;
  playerName: string;
  isHost: boolean;
}

export async function createRoom(
  hostName: string,
  setting?: string,
): Promise<ApiResponse<CreateRoomResponse>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/rooms`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        hostName,
        setting,
        language: "Arabic",
      }),
    });

    if (!response.ok) {
      const error = await response.json();
      return { error: error.error || "Failed to create room" };
    }

    const data = await response.json();
    return { data };
  } catch (error) {
    return { error: "Connection error. Please try again." };
  }
}

export async function joinRoom(
  roomCode: string,
  playerName: string,
): Promise<ApiResponse<JoinRoomResponse>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/rooms/join`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        roomCode,
        playerName,
      }),
    });

    if (!response.ok) {
      const error = await response.json();
      return { error: error.error || "Failed to join room" };
    }

    const data = await response.json();
    return { data };
  } catch (error) {
    return { error: "Connection error. Please try again." };
  }
}

export async function getRoomInfo(roomCode: string): Promise<ApiResponse<any>> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/rooms/${roomCode}`);

    if (!response.ok) {
      const error = await response.json();
      return { error: error.error || "Room not found" };
    }

    const data = await response.json();
    return { data };
  } catch (error) {
    return { error: "Connection error. Please try again." };
  }
}
