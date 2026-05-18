package com.example.movieflux.view.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.performance.LogRecompositions
import com.example.movieflux.performance.logDrawTime
import kotlin.math.roundToInt

@Composable
fun MovieListItem(
    movie: MovieModel,
    onClick: (Int) -> Unit,
    onToggleFavorite: (MovieModel) -> Unit,
    modifier: Modifier = Modifier
) {
    LogRecompositions("MovieListItem[${movie.id}]")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .logDrawTime("MovieListItem[${movie.id}]")
            .clickable { onClick(movie.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 72.dp, height = 108.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "${(movie.rating / 2).roundToInt()}/5",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = movie.overview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            if (movie.genreNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    movie.genreNames.take(2).forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = { onToggleFavorite(movie) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (movie.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (movie.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
