import { Download, Link2, Share2, X } from 'lucide-react';
import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import type { HeaderInfo, Track } from '@/data/music/types';

export type ShareCardFormat = 'post' | 'story';

interface PlaylistShareDialogProps {
  info: HeaderInfo;
  tracks: Track[];
  initialSelection?: string[];
  onClose: () => void;
}

const limits: Record<ShareCardFormat, number> = { post: 5, story: 7 };

function hexFromRgb(r: number, g: number, b: number) {
  return `#${[r, g, b].map((value) => Math.round(value).toString(16).padStart(2, '0')).join('')}`;
}

function useDominantColor(src: string | null) {
  const [color, setColor] = useState('#4c1d95');
  useEffect(() => {
    if (!src) return;
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.referrerPolicy = 'no-referrer';
    image.src = src;
    image.decode().then(() => {
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 10;
      const context = canvas.getContext('2d', { willReadFrequently: true });
      if (!context) return;
      context.drawImage(image, 0, 0, 10, 10);
      const { data } = context.getImageData(0, 0, 10, 10);
      let r = 0;
      let g = 0;
      let b = 0;
      for (let i = 0; i < data.length; i += 4) {
        r += data[i]!;
        g += data[i + 1]!;
        b += data[i + 2]!;
      }
      setColor(hexFromRgb(r / 100, g / 100, b / 100));
    }).catch(() => undefined);
  }, [src]);
  return color;
}

function safeFileName(title: string) {
  return `${title.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase() || 'playlist'}-share.png`;
}

export function PlaylistShareDialog({ info, tracks, initialSelection, onClose }: PlaylistShareDialogProps) {
  const [format, setFormat] = useState<ShareCardFormat>('post');
  const [selected, setSelected] = useState<string[]>(() => initialSelection?.length ? initialSelection : tracks.slice(0, 5).map((track) => track.videoId));
  const [message, setMessage] = useState('');
  const dominant = useDominantColor(info.thumbnail);
  const limit = limits[format];
  const selectedTracks = selected.map((id) => tracks.find((track) => track.videoId === id)).filter((track): track is Track => !!track);
  const author = info.artists.map((artist) => artist.name).join(', ') || 'Sonora listener';
  const cardStyle = useMemo(() => ({
    '--share-color': dominant,
    backgroundImage: `linear-gradient(145deg, color-mix(in srgb, ${dominant} 92%, #000), color-mix(in srgb, ${dominant} 48%, #050505) 58%, #090909)`,
  }) as CSSProperties, [dominant]);

  useEffect(() => {
    setSelected((current) => current.slice(0, limit));
  }, [limit]);

  const toggleTrack = (videoId: string) => {
    setSelected((current) => current.includes(videoId)
      ? current.filter((id) => id !== videoId)
      : current.length < limit ? [...current, videoId] : current);
  };

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setMessage('Playlist link copied');
    } catch {
      setMessage('Copy is unavailable in this browser');
    }
  };

  const shareLink = async () => {
    if (navigator.share) {
      await navigator.share({ title: info.title, text: `${info.title} by ${author}`, url: window.location.href }).catch(() => undefined);
    } else {
      await copyLink();
    }
  };

  const downloadCard = async () => {
    const card = document.getElementById('playlist-share-card');
    if (!card) return;
    const canvas = document.createElement('canvas');
    const width = format === 'post' ? 1080 : 1080;
    const height = format === 'post' ? 1080 : 1920;
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return;

    context.fillStyle = dominant;
    context.fillRect(0, 0, width, height);
    if (info.thumbnail) {
      try {
        const artwork = new Image();
        artwork.crossOrigin = 'anonymous';
        artwork.referrerPolicy = 'no-referrer';
        artwork.src = info.thumbnail;
        await artwork.decode();
        context.globalAlpha = 0.45;
        context.globalCompositeOperation = 'screen';
        context.drawImage(artwork, 0, 0, width, height);
        context.globalCompositeOperation = 'source-over';
        context.globalAlpha = 1;
      } catch {
        // Keep the extracted-color background when an image host blocks canvas access.
      }
    }
    const overlay = context.createLinearGradient(0, 0, 0, height);
    overlay.addColorStop(0, 'rgba(0, 0, 0, 0.2)');
    overlay.addColorStop(0.58, 'rgba(0, 0, 0, 0.2)');
    overlay.addColorStop(1, 'rgba(0, 0, 0, 0.85)');
    context.fillStyle = overlay;
    context.fillRect(0, 0, width, height);

    context.fillStyle = '#ffffff';
    const left = 80;
    const titleY = format === 'post' ? 740 : 1340;
    context.font = `700 ${format === 'post' ? 62 : 68}px Arial`;
    context.fillText(info.title.slice(0, 24), left, titleY);
    context.font = '400 30px Arial';
    context.fillStyle = 'rgba(255, 255, 255, 0.78)';
    context.fillText((info.description || 'A playlist worth sharing.').replace(/\s+/g, ' ').slice(0, 48), left, titleY + 52);
    context.font = '500 34px Arial';
    context.fillText(`by ${author}`.slice(0, 42), left, titleY + 100);
    context.fillStyle = '#ffffff';
    selectedTracks.forEach((track, index) => context.fillText(`${index + 1}. ${track.title.slice(0, 34)}`, left, titleY + 170 + index * 42));
    const link = document.createElement('a');
    link.download = safeFileName(info.title);
    link.href = canvas.toDataURL('image/png');
    link.click();
    setMessage('Card downloaded');
  };

  return (
    <div className="fixed inset-0 z-[10001] grid place-items-center bg-black/70 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="Share playlist">
      <div className="grid max-h-[min(900px,calc(100vh-32px))] w-full max-w-[1040px] grid-cols-[minmax(0,0.9fr)_minmax(320px,1fr)] overflow-hidden rounded-[24px] bg-page shadow-2xl max-md:grid-cols-1">
        <section className="flex min-h-0 flex-col gap-4 overflow-y-auto p-5 max-md:max-h-[42vh]">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="type-title-2-emphasized">Share playlist</h2>
              <p className="mt-1 type-callout text-secondary">Choose songs for your Spotify-style card.</p>
            </div>
            <button className="grid size-8 place-items-center rounded-full bg-quinary" aria-label="Close" onClick={onClose}><X size={18} /></button>
          </div>
          <div className="grid grid-cols-2 rounded-full bg-segmented p-1 type-callout-emphasized">
            {(['post', 'story'] as ShareCardFormat[]).map((value) => (
              <button key={value} className={`rounded-full py-2 capitalize ${format === value ? 'bg-segmented-selected text-primary shadow-sm' : 'text-secondary'}`} onClick={() => setFormat(value)}>{value} <span className="text-tertiary">({limits[value]})</span></button>
            ))}
          </div>
          <div className="flex items-center justify-between type-callout text-secondary">
            <span>Select up to {limit} songs</span><span className="tabular-nums">{selectedTracks.length}/{limit}</span>
          </div>
          <div className="min-h-0 divide-y divide-divider overflow-y-auto rounded-xl bg-quinary">
            {tracks.map((track) => {
              const checked = selected.includes(track.videoId);
              const disabled = !checked && selected.length >= limit;
              return (
                <label key={track.videoId} className={`flex cursor-pointer items-center gap-3 px-3 py-2.5 ${disabled ? 'opacity-40' : 'hover:bg-tracklist-hover'}`}>
                  <input type="checkbox" checked={checked} disabled={disabled} onChange={() => toggleTrack(track.videoId)} className="accent-key" />
                  <div className="min-w-0"><div className="truncate type-body text-primary">{track.title}</div><div className="truncate type-callout text-secondary">{track.artists.map((artist) => artist.name).join(', ')}</div></div>
                </label>
              );
            })}
          </div>
          <div className="flex flex-wrap gap-2">
            <button className="inline-flex items-center gap-2 rounded-full bg-pill-bg px-3 py-2 type-callout-emphasized text-pill" onClick={() => void shareLink()}><Share2 size={15} /> Share link</button>
            <button className="inline-flex items-center gap-2 rounded-full bg-quinary px-3 py-2 type-callout-emphasized" onClick={() => void copyLink()}><Link2 size={15} /> Copy link</button>
            <button className="inline-flex items-center gap-2 rounded-full bg-quinary px-3 py-2 type-callout-emphasized" onClick={downloadCard}><Download size={15} /> Download card</button>
          </div>
          {message && <p className="type-callout text-secondary" role="status">{message}</p>}
        </section>
        <section className="grid place-items-center bg-black/10 p-6 max-md:p-4">
          <div id="playlist-share-card" className={`relative w-full max-w-[390px] overflow-hidden rounded-[22px] p-6 text-white shadow-2xl ${format === 'story' ? 'aspect-[9/16]' : 'aspect-square'}`} style={cardStyle}>
            {info.thumbnail && <img src={info.thumbnail} alt="" className="absolute inset-0 size-full object-cover opacity-45 mix-blend-screen blur-[1px]" />}
            <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-black/20" />
            <div className="relative flex h-full flex-col justify-between">
              <div className="flex items-center justify-between"><span className="type-title-3-emphasized tracking-tight">Sonora</span><span className="rounded-full border border-white/40 px-2 py-1 type-caption-2 uppercase tracking-[0.18em]">{format}</span></div>
              <div>
                <p className="mb-2 line-clamp-2 type-callout text-white/75">{info.description || 'A playlist worth sharing.'}</p>
                <h3 className="line-clamp-3 text-[clamp(24px,4vw,44px)] font-bold leading-[1.03] tracking-[-0.04em]">{info.title}</h3>
                <p className="mt-2 truncate type-body text-white/75">By {author}</p>
                <div className="mt-5 space-y-2 border-t border-white/25 pt-4">{selectedTracks.map((track, index) => <div key={track.videoId} className="flex gap-2 type-body"><span className="w-4 shrink-0 text-white/55">{index + 1}</span><span className="truncate">{track.title}</span></div>)}</div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
