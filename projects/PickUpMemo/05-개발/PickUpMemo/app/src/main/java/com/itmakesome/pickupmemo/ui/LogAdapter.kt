package com.itmakesome.pickupmemo.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.itmakesome.pickupmemo.data.LogEntry
import com.itmakesome.pickupmemo.databinding.ItemLogBinding

/**
 * 로그 목록 RecyclerView 어댑터 (F-1 #11).
 *
 * 각 행은 [LogEntry.toLogBlock]이 만드는 설계서 5-2 고정 형식 블록
 * (`[시각]` / `Package:` / `Type:` / `Text:` 또는 `Title:`·`Body:`)을 한 TextView에 그대로 표시한다.
 * 목록은 [LogRepository.query]가 반환하는 최신순(index 0 = 최신) 스냅샷을 그대로 사용한다.
 *
 * 단순 검증 도구이므로 DiffUtil 대신 [submit] + notifyDataSetChanged로 갱신한다(목록은 화면 진입/onResume 시에만 교체).
 */
class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val items = ArrayList<LogEntry>()

    fun submit(newItems: List<LogEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: LogEntry) {
            binding.tvLogBlock.text = entry.toLogBlock()
        }
    }
}
