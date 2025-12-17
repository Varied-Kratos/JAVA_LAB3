import java.util.concurrent.atomic.AtomicInteger;

public class PassengerRequest implements Comparable<PassengerRequest> {
    private final int id;
    private final int floor;
    private final Direction direction;
    private final int targetFloor;
    private final long timestamp;
    private final RequestType type;
    private final int priority;

    private static final AtomicInteger idGenerator = new AtomicInteger(1);

    public PassengerRequest(int floor, Direction direction, int targetFloor, RequestType type, int priority) {
        this.id = idGenerator.getAndIncrement();
        this.floor = floor;
        this.direction = direction;
        this.targetFloor = targetFloor;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.priority = Math.max(1, Math.min(10, priority));
    }

    public PassengerRequest(int floor, Direction direction, int targetFloor) {
        this(floor, direction, targetFloor, RequestType.CALL, 5);
    }

    public PassengerRequest(int floor, Direction direction, int targetFloor, RequestType type) {
        this(floor, direction, targetFloor, type,
                type == RequestType.EMERGENCY ? 10 :
                        type == RequestType.PRIORITY ? 8 : 5);
    }

    public int getId() { return id; }
    public int getFloor() { return floor; }
    public Direction getDirection() { return direction; }
    public int getTargetFloor() { return targetFloor; }
    public long getTimestamp() { return timestamp; }
    public RequestType getType() { return type; }
    public int getPriority() { return priority; }

    @Override
    public int compareTo(PassengerRequest other) {
        if (this.priority != other.priority) {
            return Integer.compare(other.priority, this.priority);
        }
        return Long.compare(this.timestamp, other.timestamp);
    }

    @Override
    public String toString() {
        return String.format("Запрос#%d: этаж %d -> %d (%s, тип: %s, приоритет: %d)",
                id, floor, targetFloor, direction, type, priority);
    }
}