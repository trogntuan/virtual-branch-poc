import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ConnectionState as LiveKitConnectionState,
  Room,
  RoomEvent,
  Track,
} from 'livekit-client';
import type { RoomConnectionState } from './types';

function mapConnectionState(state: LiveKitConnectionState): RoomConnectionState {
  switch (state) {
    case LiveKitConnectionState.Connected:
      return 'CONNECTED';
    case LiveKitConnectionState.Reconnecting:
      return 'RECONNECTING';
    case LiveKitConnectionState.Connecting:
      return 'CONNECTING';
    default:
      return 'DISCONNECTED';
  }
}

function attachVideoTrack(track: Track, element: HTMLVideoElement | null) {
  if (!element) {
    return;
  }
  track.attach(element);
  element.playsInline = true;
  void element.play().catch(() => undefined);
}

function attachRoomVideos(
  room: Room,
  localElement: HTMLVideoElement | null,
  remoteElement: HTMLVideoElement | null,
) {
  const cameraPublication = room.localParticipant.getTrackPublication(Track.Source.Camera);
  if (cameraPublication?.track) {
    attachVideoTrack(cameraPublication.track, localElement);
  }

  room.remoteParticipants.forEach((participant) => {
    participant.trackPublications.forEach((publication) => {
      if (publication.track && publication.kind === Track.Kind.Video) {
        attachVideoTrack(publication.track, remoteElement);
      }
    });
  });
}

export function useLiveKitRoom() {
  const roomRef = useRef<Room | null>(null);
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);

  const setLocalVideoEl = useCallback((element: HTMLVideoElement | null) => {
    localVideoRef.current = element;
    const room = roomRef.current;
    if (element && room) {
      attachRoomVideos(room, element, remoteVideoRef.current);
    }
  }, []);

  const setRemoteVideoEl = useCallback((element: HTMLVideoElement | null) => {
    remoteVideoRef.current = element;
    const room = roomRef.current;
    if (element && room) {
      attachRoomVideos(room, localVideoRef.current, element);
    }
  }, []);

  const [room, setRoom] = useState<Room | null>(null);
  const [connectionState, setConnectionState] = useState<RoomConnectionState>('DISCONNECTED');
  const [micEnabled, setMicEnabled] = useState(true);
  const [cameraEnabled, setCameraEnabled] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const cleanupRemoteVideo = useCallback(() => {
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = null;
    }
  }, []);

  const disconnect = useCallback(async () => {
    const current = roomRef.current;
    roomRef.current = null;
    setRoom(null);
    if (current) {
      current.removeAllListeners();
      await current.disconnect();
    }
    cleanupRemoteVideo();
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = null;
    }
    setConnectionState('DISCONNECTED');
  }, [cleanupRemoteVideo]);

  useEffect(() => {
    return () => {
      void disconnect();
    };
  }, [disconnect]);

  const connect = useCallback(
    async (serverUrl: string, token: string) => {
      await disconnect();
      setConnectionState('CONNECTING');
      setError(null);

      const nextRoom = new Room({
        adaptiveStream: true,
        dynacast: true,
      });

      // Assign before connect so ConnectionStateChanged re-renders never see a null room.
      roomRef.current = nextRoom;
      setRoom(nextRoom);

      nextRoom.on(RoomEvent.ConnectionStateChanged, (state) => {
        if (roomRef.current !== nextRoom) return;
        setConnectionState(mapConnectionState(state));
      });

      nextRoom.on(RoomEvent.TrackSubscribed, (track) => {
        if (track.kind === Track.Kind.Video) {
          attachVideoTrack(track, remoteVideoRef.current);
        }
      });

      nextRoom.on(RoomEvent.TrackUnsubscribed, (track) => {
        track.detach();
        if (track.kind === Track.Kind.Video) {
          cleanupRemoteVideo();
        }
      });

      nextRoom.on(RoomEvent.LocalTrackPublished, (publication) => {
        if (publication.source === Track.Source.Camera && publication.track) {
          attachVideoTrack(publication.track, localVideoRef.current);
        }
      });

      nextRoom.on(RoomEvent.Disconnected, () => {
        if (roomRef.current !== nextRoom) return;
        roomRef.current = null;
        setRoom(null);
        setConnectionState('DISCONNECTED');
      });

      try {
        await nextRoom.connect(serverUrl, token);

        await nextRoom.localParticipant.setMicrophoneEnabled(true);
        await nextRoom.localParticipant.setCameraEnabled(true);
        setMicEnabled(true);
        setCameraEnabled(true);

        attachRoomVideos(nextRoom, localVideoRef.current, remoteVideoRef.current);

        await nextRoom.startAudio();
        setConnectionState('CONNECTED');
      } catch (cause) {
        if (roomRef.current === nextRoom) {
          roomRef.current = null;
          setRoom(null);
        }
        nextRoom.removeAllListeners();
        await nextRoom.disconnect();

        if (cause instanceof DOMException && cause.name === 'NotAllowedError') {
          setError('Chưa được cấp quyền camera hoặc micro.');
        } else if (cause instanceof Error) {
          setError(cause.message || 'Không kết nối được LiveKit.');
        } else {
          setError('Không kết nối được LiveKit.');
        }

        setConnectionState('DISCONNECTED');
      }
    },
    [cleanupRemoteVideo, disconnect],
  );

  const toggleMic = useCallback(async () => {
    const current = roomRef.current;
    if (!current) {
      return;
    }
    const next = !micEnabled;
    await current.localParticipant.setMicrophoneEnabled(next);
    setMicEnabled(next);
  }, [micEnabled]);

  const toggleCamera = useCallback(async () => {
    const current = roomRef.current;
    if (!current) {
      return;
    }
    const next = !cameraEnabled;
    await current.localParticipant.setCameraEnabled(next);
    setCameraEnabled(next);

    if (!next && localVideoRef.current) {
      localVideoRef.current.srcObject = null;
    } else {
      const cameraPublication = current.localParticipant.getTrackPublication(Track.Source.Camera);
      if (cameraPublication?.track) {
        attachVideoTrack(cameraPublication.track, localVideoRef.current);
      }
    }
  }, [cameraEnabled]);

  return {
    connect,
    disconnect,
    toggleMic,
    toggleCamera,
    room,
    connectionState,
    micEnabled,
    cameraEnabled,
    error,
    localVideoRef: setLocalVideoEl,
    remoteVideoRef: setRemoteVideoEl,
    getRoom: () => roomRef.current,
  };
}
