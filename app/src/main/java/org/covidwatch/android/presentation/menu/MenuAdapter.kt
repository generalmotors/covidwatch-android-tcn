package org.covidwatch.android.presentation.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.covidwatch.android.R

class MenuAdapter(
    private val onClick: ((destination: Destination) -> Unit)
) : RecyclerView.Adapter<MenuItemViewHolder>() {

    private val items = listOf(
        MenuItem(R.string.settings, 0, Settings),
        MenuItem(R.string.terms_of_use, R.drawable.ic_exit_to_app, Browser("https://www.your_privacy_url.com/")),
        MenuItem(R.string.privacy_policy, R.drawable.ic_exit_to_app, Browser("https://www.your_privacy_url.com/"))
       // MenuItem(R.string.debug_info, R.drawable.ic_info_red, DebugInfo)
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_menu, parent, false)
        return MenuItemViewHolder(root)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MenuItemViewHolder, position: Int) {
        val menuItem = items[position]
        holder.bind(menuItem)

        holder.itemView.setOnClickListener {
            onClick(menuItem.destination)
        }
    }
}