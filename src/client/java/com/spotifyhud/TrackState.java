package com.spotifyhud;

public class TrackState {
	public String title = "";
	public String artists = "";
	public String coverUrl = "";
	public String trackId = "";
	public String albumName = "";
	public long progressMs = 0L;
	public long durationMs = 0L;
	public boolean playing = false;
	public boolean shuffle = false;
	public String repeat = "off";
	public long volumePercent = -1L;
	public String deviceId = "";

	public long capturedAtMillis = 0L;

	public long currentProgressMs() {
		if (!playing) {
			return progressMs;
		}
		return progressMs + (System.currentTimeMillis() - capturedAtMillis);
	}

	public boolean coversChanged(TrackState other) {
		if (other == null) {
			return coverUrl != null && !coverUrl.isEmpty();
		}
		return !coverUrl.equals(other.coverUrl);
	}
}
