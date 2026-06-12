package doom.despair.server

import doom.despair.Ship
import doom.despair.core.CellState
import doom.despair.core.CellView
import doom.despair.core.ShipFactory
import doom.despair.ships.ShipType

class Board {
    @JvmRecord
    data class Coordinate(val x: Int, val y: Int)

    private data class PlacedShip(
        val type: ShipType,
        val start: Coordinate,
        val horizontal: Boolean,
        val size: Int
    )

    data class ShotResult(
        val valid: Boolean,
        val hit: Boolean,
        val sunk: Boolean,
        val alreadyTried: Boolean,
        val won: Boolean
    )

    private val grid: MutableMap<Coordinate, Ship> = HashMap()
    private val hits: MutableSet<Coordinate> = HashSet()
    private val misses: MutableSet<Coordinate> = HashSet()
    private val placements: MutableMap<Ship, PlacedShip> = HashMap()

    fun getShipAt(coord: Coordinate?): Ship? {
        return grid[coord]
    }

    fun placeShip(type: ShipType?, start: Coordinate, horizontal: Boolean): Boolean {
        val shipType = type ?: throw RuntimeException("Invalid ship type: null")
        val ship = ShipFactory.createShip(shipType)
        val temp: MutableMap<Coordinate, Ship> = HashMap()
        for (i in 0 until ship.size) {
            val coord = if (horizontal) Coordinate(start.x + i, start.y) else Coordinate(start.x, start.y + i)
            if (!isWithinBounds(coord) || grid.containsKey(coord)) {
                return false
            }
            temp[coord] = ship
        }
        grid.putAll(temp)
        placements[ship] = PlacedShip(shipType, start, horizontal, ship.size)
        return true
    }

    fun fireAt(coord: Coordinate): ShotResult {
        if (!isWithinBounds(coord)) {
            return ShotResult(valid = false, hit = false, sunk = false, alreadyTried = false, won = false)
        }
        if (hits.contains(coord) || misses.contains(coord)) {
            return ShotResult(valid = false, hit = false, sunk = false, alreadyTried = true, won = false)
        }

        val ship = grid[coord]
        if (ship == null) {
            misses.add(coord)
            return ShotResult(valid = true, hit = false, sunk = false, alreadyTried = false, won = false)
        }

        hits.add(coord)
        val shipCoordinates = grid.filterValues { it === ship }.keys
        val sunk = shipCoordinates.all { hits.contains(it) }
        val won = !grid.keys.any { !hits.contains(it) }
        return ShotResult(valid = true, hit = true, sunk = sunk, alreadyTried = false, won = won)
    }

    fun toView(revealShips: Boolean): List<CellView> {
        val cells = ArrayList<CellView>()
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val c = Coordinate(x, y)
                val state = when {
                    hits.contains(c) -> CellState.HIT
                    misses.contains(c) -> CellState.MISS
                    revealShips && grid.containsKey(c) -> CellState.SHIP
                    else -> CellState.UNKNOWN
                }
                if (state != CellState.UNKNOWN) {
                    val ship = grid[c]
                    val placement = placements[ship]
                    val shouldReveal = ship != null && placement != null && (
                        revealShips ||
                        (state == CellState.HIT && grid.filterValues { it === ship }.keys.all { hits.contains(it) })
                    )
                    if (shouldReveal) {
                        val segment = calculateSegment(placement, c)
                        cells.add(CellView(x, y, state, placement.type, segment, placement.horizontal))
                    } else {
                        cells.add(CellView(x, y, state))
                    }
                }
            }
        }
        return cells
    }

    private fun calculateSegment(placement: PlacedShip, coord: Coordinate): Int {
        val offset = if (placement.horizontal) {
            coord.x - placement.start.x
        } else {
            coord.y - placement.start.y
        }
        return offset + 1
    }

    fun hasPlacedShips(): Boolean = grid.isNotEmpty()

    private fun isWithinBounds(coord: Coordinate): Boolean {
        return coord.x in 0..9 && coord.y in 0..9
    }
}
