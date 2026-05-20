package com.example.movieflux.view.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieflux.R
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.ui.theme.MovieFluxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Isolated tests for [MovieCard] — the shared movie tile rendered on Home, Favorites, and
 * (via the favorite affordance) reflected from Details. Driven with a fixed [MovieModel] so
 * rendering and callbacks are deterministic and require no network.
 */
class MovieCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun movie(isFavorite: Boolean) = MovieModel(
        id = 1,
        title = "The Matrix",
        overview = "A hacker discovers reality.",
        posterUrl = "https://example.com/poster.jpg",
        rating = 8.7,
        genreIds = listOf(28, 878),
        isFavorite = isFavorite,
        genreNames = listOf("Action", "Sci-Fi")
    )

    @Test
    fun rendersTitleRatingAndGenreChips() {
        composeTestRule.setContent {
            MovieFluxTheme {
                MovieCard(movie = movie(isFavorite = false), onClick = {}, onToggleFavorite = {})
            }
        }

        composeTestRule.onNodeWithText("The Matrix").assertIsDisplayed()
        // Rating is formatted with the default locale ("%.1f"), so the decimal separator is
        // locale-dependent (e.g. "8.7" vs "8,7"). Compute the expectation the same way.
        composeTestRule.onNodeWithText("%.1f".format(8.7)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sci-Fi").assertIsDisplayed()
    }

    @Test
    fun nonFavorite_showsAddAffordance_andTogglesOnClick() {
        var toggled = false
        composeTestRule.setContent {
            MovieFluxTheme {
                MovieCard(movie = movie(isFavorite = false), onClick = {}, onToggleFavorite = { toggled = true })
            }
        }

        val addCd = context.getString(R.string.cd_add_favorite)
        composeTestRule.onNodeWithContentDescription(addCd).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(addCd).performClick()
        assertTrue(toggled)
    }

    @Test
    fun favorite_showsRemoveAffordance() {
        composeTestRule.setContent {
            MovieFluxTheme {
                MovieCard(movie = movie(isFavorite = true), onClick = {}, onToggleFavorite = {})
            }
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_remove_favorite))
            .assertIsDisplayed()
    }

    @Test
    fun tappingCard_invokesOnClick() {
        var clicked = false
        composeTestRule.setContent {
            MovieFluxTheme {
                MovieCard(movie = movie(isFavorite = false), onClick = { clicked = true }, onToggleFavorite = {})
            }
        }

        composeTestRule.onNodeWithText("The Matrix").performClick()
        assertTrue(clicked)
    }
}
