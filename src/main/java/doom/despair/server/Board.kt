package doom.despair.server

import doom.despair.Ship
import doom.despair.core.CellState
import doom.despair.core.CellView
import doom.despair.core.ShipFactory
import doom.despair.ships.ShipType

class Board {
    @JvmRecord
    data class Coordinate(val x: Int, val y: Int)

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
                    cells.add(CellView(x, y, state))
                }
            }
        }
        return cells
    }

    fun hasPlacedShips(): Boolean = grid.isNotEmpty()

    private fun isWithinBounds(coord: Coordinate): Boolean {
        return coord.x in 0..9 && coord.y in 0..9
    }
}
