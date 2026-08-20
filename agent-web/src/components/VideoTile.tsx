import type { RefCallback } from 'react';

interface VideoTileProps {
  label: string;
  videoRef: RefCallback<HTMLVideoElement>;
  muted?: boolean;
}

export function VideoTile({ label, videoRef, muted = false }: VideoTileProps) {
  return (
    <div className="video-tile">
      <div className="video-tile-label">{label}</div>
      <video ref={videoRef} autoPlay playsInline muted={muted} className="video-element" />
    </div>
  );
}
