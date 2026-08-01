import { showToast } from './toast.js';

/**
 * The film loads on demand. Until the poster is clicked the page has made no
 * request to the video host at all.
 */
export function initVideo() {
  const frame = document.getElementById('video');
  const play = document.getElementById('video-play');
  if (!frame || !play) return;

  play.addEventListener('click', () => {
    const url = (frame.dataset.videoUrl || '').trim();

    if (!url) {
      showToast('Film link not published yet');
      return;
    }

    // Clears the poster and the play control before the player takes the frame.
    frame.replaceChildren();

    if (/\.(mp4|webm)$/i.test(url)) {
      const video = document.createElement('video');
      video.src = url;
      video.controls = true;
      video.autoplay = true;
      video.playsInline = true;
      frame.append(video);
      return;
    }

    const iframe = document.createElement('iframe');
    iframe.src = url;
    iframe.title = 'DMTools film';
    iframe.allow =
      'accelerometer; autoplay; clipboard-write; encrypted-media; picture-in-picture';
    iframe.allowFullscreen = true;
    frame.append(iframe);
  });
}
