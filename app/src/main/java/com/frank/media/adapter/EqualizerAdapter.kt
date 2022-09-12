package com.frank.media.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.frank.media.R
import com.frank.media.listener.OnSeeBarListener

import java.util.ArrayList

class EqualizerAdapter(private val context: Context, private val onSeeBarListener: OnSeeBarListener?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var equalizerList: List<Pair<*, *>>? = ArrayList()
    private val seekBarList = ArrayList<SeekBar>()
    private var maxProgress: Int = 0

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): RecyclerView.ViewHolder {
        return EqualizerHolder(LayoutInflater.from(context).inflate(R.layout.adapter_equalizer, null))
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, @SuppressLint("RecyclerView") i: Int) {
        val holder = viewHolder as EqualizerHolder
        if (equalizerList != null) {
            val centerFreq = equalizerList!![i].first as String
            holder.txtFrequency.text = centerFreq
        }
        seekBarList.add(holder.barEqualizer)
        holder.barEqualizer.max = maxProgress
        val currentProgress = equalizerList!![i].second as Int
        holder.barEqualizer.progress = currentProgress

        holder.barEqualizer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onSeeBarListener?.onProgress(i, seekBar.progress)
            }
        })
    }

    override fun getItemCount(): Int {
        return if (equalizerList != null) equalizerList!!.size else 0
    }

    private inner class EqualizerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val txtFrequency: TextView = itemView.findViewById(R.id.txt_frequency)
        val barEqualizer: SeekBar = itemView.findViewById(R.id.bar_equalizer)

    }

    fun setMaxProgress(maxProgress: Int) {
        if (maxProgress > 0) {
            this.maxProgress = maxProgress
        }
    }

    fun setEqualizerList(equalizerList: List<Pair<*, *>>?) {
        if (equalizerList != null) {
            this.equalizerList = equalizerList
            notifyDataSetChanged()
        }
    }

    fun getSeekBarList(): List<SeekBar> {
        return seekBarList
    }

}