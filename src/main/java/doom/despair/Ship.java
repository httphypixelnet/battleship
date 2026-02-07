package doom.despair;

import doom.despair.core.PlayerManagedObject;
import doom.despair.server.Board;

import java.util.List;

public abstract class Ship extends PlayerManagedObject {
    private List<Board.Coordinate> coordinates;
    public abstract List<Board.Coordinate> getCoordinates();
    public Ship(List<Board.Coordinate> coords) { coordinates = coords; }
}
