package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.playback.PlaybackService
import kotlinx.android.synthetic.main.item_track_queue.view.track_queue_drag_handle
import kotlinx.android.synthetic.main.item_track_queue.view.track_queue_duration
import kotlinx.android.synthetic.main.item_track_queue.view.track_queue_frame
import kotlinx.android.synthetic.main.item_track_queue.view.track_queue_title

class QueueAdapter(activity: SimpleActivity, items: ArrayList<Track>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<Track>(items, activity, recyclerView, itemClick), ItemTouchHelperContract, RecyclerViewFastScroller.OnPopupTextUpdate {

    private var startReorderDragListener: StartReorderDragListener

    init {
        setupDragListener(true)

        val touchHelper = ItemTouchHelper(ItemMoveCallback(this))
        touchHelper.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                touchHelper.startDrag(viewHolder)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_queue

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_track_queue, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bindView(item, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, item, holder)
        }
        bindViewHolder(holder)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_remove_from_queue -> removeFromQueue()
            R.id.cab_delete_file -> deleteTracks()
            R.id.cab_share -> shareFiles()
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun onActionModeCreated() = notifyDataChanged()

    override fun onActionModeDestroyed() = notifyDataChanged()

    private fun removeFromQueue() {
        val positions = ArrayList<Int>()
        val selectedTracks = getSelectedTracks()
        selectedTracks.forEach { track ->
            val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
            if (position != -1) {
                positions.add(position)
            }
        }

        ctx.removeQueueItems(selectedTracks) {
            refreshTracksList(positions)
        }
    }

    private fun deleteTracks() {
        ConfirmationDialog(ctx, "", R.string.delete_song_warning, R.string.ok, R.string.cancel) {
            val positions = ArrayList<Int>()
            val selectedTracks = getSelectedTracks()
            selectedTracks.forEach { track ->
                val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                if (position != -1) {
                    positions.add(position)
                }
            }

            ctx.deleteTracks(selectedTracks) {
                refreshTracksList(positions)
            }
        }
    }

    private fun refreshTracksList(positions: ArrayList<Int>) {
        ctx.runOnUiThread {
            positions.sortDescending()
            positions.forEach {
                items.removeAt(it)
            }

            removeSelectedItems(positions)
            if (items.isEmpty()) {
                ctx.finish()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(view: View, track: Track, holder: ViewHolder) {
        view.apply {
            setupViewBackground(ctx)
            track_queue_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_queue_title.text = if (textToHighlight.isEmpty()) track.title else track.title.highlightTextPart(textToHighlight, properPrimaryColor)

            val currentTrack = PlaybackService.currentMediaItem?.toTrack()
            arrayOf(track_queue_title, track_queue_duration).forEach {
                val color = if (track.mediaStoreId == currentTrack?.mediaStoreId) context.getProperPrimaryColor() else textColor
                it.setTextColor(color)
            }

            track_queue_duration.text = track.duration.getFormattedDuration()
            track_queue_drag_handle.beVisibleIf(selectedKeys.isNotEmpty())
            track_queue_drag_handle.applyColorFilter(textColor)
            track_queue_drag_handle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }

            ctx.getTrackCoverArt(track) { coverArt ->
                loadImage(findViewById(R.id.track_queue_image), coverArt, placeholderBig)
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        items.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        ctx.withPlayer {
            moveMediaItem(fromPosition, toPosition)
        }
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {}

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(ctx.config.trackSorting) ?: ""
}
