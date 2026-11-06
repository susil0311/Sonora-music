import { Share2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useParams } from 'react-router';
import { DetailHeader, detailButton } from '@/components/music/DetailHeader';
import { playList, shuffleList } from '@/components/music/play';
import { LoadError, LoadMoreError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { PlaylistShareDialog } from '@/components/music/PlaylistShareDialog';
import { usePlaylist, useYouTubeReady } from '@/data/music/queries';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { PlaylistSkeleton } from '@/skeletons/PageSkeletons';
import { detailFooter } from './pageStyles';

/** A playlist (report §14): header, then the artwork track list, loading on as you scroll. */
export default function PlaylistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const playlist = usePlaylist(id);
  const header = playlist.data?.pages[0]?.header ?? null;
  const [shareOpen, setShareOpen] = useState(false);
  const [selectedTrackIds, setSelectedTrackIds] = useState<Set<string>>(() => new Set());
  usePageTint(useArtworkColor(header?.thumbnail));

  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = playlist;
  const onNearEnd = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage && !isFetchNextPageError) void fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage]);

  if (missing) return <NeedsExtension />;
  if (playlist.isError && !playlist.data) return <LoadError error={playlist.error} onRetry={() => void playlist.refetch()} />;
  if (!playlist.data || !header) return <PlaylistSkeleton />;

  const tracks = playlist.data.pages.flatMap((page) => page.tracks);
  const context = { source: { title: header.title, type: 'playlist' as const, id } };
  const toggleTrack = (track: (typeof tracks)[number]) => {
    setSelectedTrackIds((current) => {
      const next = new Set(current);
      if (next.has(track.videoId)) next.delete(track.videoId);
      else next.add(track.videoId);
      return next;
    });
  };
  return (
    <>
      <DetailHeader
        info={header}
        canPlay={tracks.length > 0}
        onPlay={() => playList(tracks, 0, context)}
        onShuffle={() => shuffleList(tracks, context)}
        share={
          <button className={detailButton.circle} aria-label="Share playlist" title="Share playlist" onClick={() => setShareOpen(true)} disabled={!tracks.length}>
            <Share2 size={18} strokeWidth={2.2} />
          </button>
        }
      />
      <TrackList
        tracks={tracks}
        variant="playlist"
        onNearEnd={onNearEnd}
        source={context.source}
        selectedTrackIds={selectedTrackIds}
        onToggleTrack={toggleTrack}
      />
      {isFetchingNextPage && <div className="flex justify-center py-5 type-callout text-secondary">Loading more songs…</div>}
      {playlist.isFetchNextPageError && <LoadMoreError onRetry={() => void fetchNextPage()} />}
      <footer className={detailFooter}>
        <p>{header.meta}</p>
      </footer>
      {shareOpen && <PlaylistShareDialog info={header} tracks={tracks} initialSelection={[...selectedTrackIds]} onClose={() => setShareOpen(false)} />}
    </>
  );
}
