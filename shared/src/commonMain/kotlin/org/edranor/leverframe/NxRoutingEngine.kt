package org.edranor.leverframe

data class NxRoute(
    val pathCells: List<Pair<Int, Int>>
)

object NxRoutingEngine {

    fun getConnections(element: SchematicElementDef, map: Map<Pair<Int, Int>, SchematicElementDef>): List<Pair<Int, Int>> {
        val x = element.x
        val y = element.y
        val conns = mutableListOf<Pair<Int, Int>>()
        
        when (element.type) {
            "STRAIGHT_H", "SIGNAL_LEFT", "SIGNAL_RIGHT", "BRACKET_SIGNAL_LEFT", "BRACKET_SIGNAL_RIGHT" -> {
                // Connects right
                conns.add(Pair(x + 1, y))
                
                // Connects left to straight
                val leftStraight = map[Pair(x - 1, y)]
                if (leftStraight != null) {
                    conns.add(Pair(x - 1, y))
                }
                // Connects left to a turnout coming from below
                val leftTurnoutUp = map[Pair(x - 1, y + 1)]
                if (leftTurnoutUp?.type == "TURNOUT_LEFT") {
                    conns.add(Pair(x - 1, y + 1))
                }
                // Connects left to a turnout coming from above
                val leftTurnoutDown = map[Pair(x - 1, y - 1)]
                if (leftTurnoutDown?.type == "TURNOUT_RIGHT") {
                    conns.add(Pair(x - 1, y - 1))
                }
                // Fallback: if there's nothing diagonal, we still report left so we don't break simple tracks
                if (leftTurnoutUp?.type != "TURNOUT_LEFT" && leftTurnoutDown?.type != "TURNOUT_RIGHT") {
                    conns.add(Pair(x - 1, y))
                }
            }
            "STRAIGHT_V" -> {
                conns.add(Pair(x, y - 1))
                conns.add(Pair(x, y + 1))
            }
            "TURNOUT_LEFT" -> {
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                conns.add(Pair(x + 1, y - 1))
            }
            "TURNOUT_RIGHT" -> {
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                conns.add(Pair(x + 1, y + 1))
            }
        }
        return conns.distinct()
    }

    fun findReachableExits(
        startX: Int,
        startY: Int,
        elements: List<SchematicElementDef>
    ): List<NxRoute> {
        val map = elements.associateBy { Pair(it.x, it.y) }
        
        fun getConnectedNeighbors(pos: Pair<Int, Int>): List<Pair<Int, Int>> {
            val elem = map[pos] ?: return emptyList()
            val neighbors = getConnections(elem, map)
            return neighbors.filter { n ->
                val neighborElem = map[n]
                // Mutual connection check
                neighborElem != null && getConnections(neighborElem, map).contains(pos)
            }
        }
        
        val routes = mutableListOf<NxRoute>()
        val queue = ArrayDeque<List<Pair<Int, Int>>>()
        queue.add(listOf(Pair(startX, startY)))
        
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()
            
            val elem = map[current]!!
            if (path.size > 1 && (elem.nxButton == NxButtonType.EXIT_ONLY || elem.nxButton == NxButtonType.BOTH)) {
                routes.add(NxRoute(path))
                continue // Stop searching this branch beyond an exit
            }
            
            val neighbors = getConnectedNeighbors(current)
            for (n in neighbors) {
                if (!path.contains(n)) {
                    queue.add(path + n)
                }
            }
        }
        return routes
    }
}
