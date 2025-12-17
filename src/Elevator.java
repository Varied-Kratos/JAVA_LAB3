import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Elevator implements Runnable {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private ElevatorStatus status;
    private final TreeSet<Integer> targetFloors = new TreeSet<>();
    private final PriorityBlockingQueue<PassengerRequest> incomingRequests;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition condition = lock.newCondition();
    private final int maxFloor;
    private final int maxCapacity;
    private int currentPassengers;
    private int totalPassengers;
    private int totalTraveledFloors;
    private final Set<Integer> priorityFloors = new HashSet<>();
    private long totalIdleTime;
    private long lastStatusChangeTime;
    private boolean emergencyMode;
    private boolean maintenanceMode;
    private final String name;

    public Elevator(int id, int startFloor, int maxFloor, int maxCapacity, String name) {
        this.id = id;
        this.currentFloor = startFloor;
        this.direction = Direction.NONE;
        this.status = ElevatorStatus.STOPPED;
        this.maxFloor = maxFloor;
        this.maxCapacity = maxCapacity;
        this.currentPassengers = 0;
        this.totalPassengers = 0;
        this.totalTraveledFloors = 0;
        this.emergencyMode = false;
        this.maintenanceMode = false;
        this.name = name != null ? name : "Лифт " + id;
        this.lastStatusChangeTime = System.currentTimeMillis();
        this.incomingRequests = new PriorityBlockingQueue<>();
        priorityFloors.add(1);
        priorityFloors.add(maxFloor);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                lock.lock();
                try {
                    if (maintenanceMode) {
                        status = ElevatorStatus.MAINTENANCE;
                        condition.await();
                        continue;
                    }

                    if (emergencyMode) {
                        status = ElevatorStatus.EMERGENCY;
                        handleEmergency();
                        continue;
                    }

                    if (status == ElevatorStatus.STOPPED && targetFloors.isEmpty()) {
                        long now = System.currentTimeMillis();
                        totalIdleTime += (now - lastStatusChangeTime);
                        lastStatusChangeTime = now;
                    }

                    processIncomingRequests();

                    if (!targetFloors.isEmpty()) {
                        Integer nextFloor = getNextTargetFloor();
                        if (nextFloor != null) {
                            moveTowards(nextFloor);
                        }
                    } else {
                        if (status != ElevatorStatus.STOPPED) {
                            status = ElevatorStatus.STOPPED;
                        }
                    }

                    checkArrival();

                } finally {
                    lock.unlock();
                }

                TimeUnit.MILLISECONDS.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleEmergency() throws InterruptedException {
        int nearestFloor = findNearestFloorForEvacuation();

        if (nearestFloor != currentFloor) {
            moveTowards(nearestFloor);
            TimeUnit.MILLISECONDS.sleep(1000);
        } else {
            openDoors();
            TimeUnit.SECONDS.sleep(5);
            closeDoors();
            emergencyMode = false;
            status = ElevatorStatus.STOPPED;
        }
    }

    private int findNearestFloorForEvacuation() {
        if (currentFloor == 1) return 1;

        int nearestPriority = Integer.MAX_VALUE;
        for (int floor : priorityFloors) {
            if (Math.abs(floor - currentFloor) < Math.abs(nearestPriority - currentFloor)) {
                nearestPriority = floor;
            }
        }

        if (nearestPriority != Integer.MAX_VALUE) {
            return nearestPriority;
        }

        return (currentFloor > 1) ? currentFloor - 1 : currentFloor + 1;
    }

    private void processIncomingRequests() {
        List<PassengerRequest> requests = new ArrayList<>();
        incomingRequests.drainTo(requests);

        for (PassengerRequest request : requests) {
            if (currentPassengers >= maxCapacity && request.getType() != RequestType.EMERGENCY) {
                continue;
            }

            addTargetFloor(request.getFloor());
            addTargetFloor(request.getTargetFloor());

            if (request.getType() != RequestType.CALL) {
                currentPassengers++;
            }
            totalPassengers++;

            if (request.getType() == RequestType.PRIORITY || request.getType() == RequestType.EMERGENCY) {
                recalculateDirection();
            }
        }
    }

    private Integer getNextTargetFloor() {
        if (targetFloors.isEmpty()) return null;

        if (direction == Direction.UP) {
            Integer next = targetFloors.ceiling(currentFloor);
            return next != null ? next : targetFloors.last();
        } else if (direction == Direction.DOWN) {
            Integer next = targetFloors.floor(currentFloor);
            return next != null ? next : targetFloors.first();
        } else {
            Integer nearest = null;
            int minDistance = Integer.MAX_VALUE;

            for (Integer floor : targetFloors) {
                int distance = Math.abs(floor - currentFloor);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = floor;
                }
            }

            if (nearest != null) {
                direction = (nearest > currentFloor) ? Direction.UP : Direction.DOWN;
            }

            return nearest;
        }
    }

    private void moveTowards(int targetFloor) {
        if (targetFloor == currentFloor) return;

        status = ElevatorStatus.MOVING;

        if (targetFloor > currentFloor) {
            currentFloor++;
            direction = Direction.UP;
        } else {
            currentFloor--;
            direction = Direction.DOWN;
        }

        totalTraveledFloors++;
    }

    private void checkArrival() throws InterruptedException {
        if (targetFloors.contains(currentFloor)) {
            stopAtFloor();
        }
    }

    private void stopAtFloor() throws InterruptedException {
        status = ElevatorStatus.DOORS_OPEN;
        log("Остановился на этаже " + currentFloor + ", открываю двери");

        targetFloors.remove(currentFloor);
        TimeUnit.SECONDS.sleep(2);

        if (priorityFloors.contains(currentFloor)) {
            TimeUnit.SECONDS.sleep(1);
        }

        int exitingPassengers = simulatePassengerExit();
        currentPassengers -= exitingPassengers;
        currentPassengers = Math.max(0, currentPassengers);

        log("Закрываю двери. Пассажиров в лифте: " + currentPassengers);
        status = ElevatorStatus.STOPPED;
        recalculateDirection();
    }

    private int simulatePassengerExit() {
        Random random = new Random();
        int exiting = 0;
        for (int i = 0; i < currentPassengers; i++) {
            if (random.nextDouble() < 0.3) {
                exiting++;
            }
        }
        return exiting;
    }

    private void openDoors() throws InterruptedException {
        status = ElevatorStatus.DOORS_OPEN;
        TimeUnit.SECONDS.sleep(1);
    }

    private void closeDoors() {
        status = ElevatorStatus.STOPPED;
    }

    private void recalculateDirection() {
        if (targetFloors.isEmpty()) {
            direction = Direction.NONE;
            return;
        }

        if (direction == Direction.UP) {
            Integer higher = targetFloors.higher(currentFloor);
            if (higher == null) {
                direction = Direction.DOWN;
            }
        } else if (direction == Direction.DOWN) {
            Integer lower = targetFloors.lower(currentFloor);
            if (lower == null) {
                direction = Direction.UP;
            }
        }
    }

    private void addTargetFloor(int floor) {
        if (floor < 1 || floor > maxFloor) {
            return;
        }
        targetFloors.add(floor);
    }

    public void addRequest(PassengerRequest request) {
        incomingRequests.offer(request);
    }

    public void setEmergencyMode(boolean emergency) {
        lock.lock();
        try {
            this.emergencyMode = emergency;
            if (emergency) {
                status = ElevatorStatus.EMERGENCY;
                incomingRequests.removeIf(req -> req.getType() != RequestType.EMERGENCY);
                targetFloors.clear();
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void setMaintenanceMode(boolean maintenance) {
        lock.lock();
        try {
            this.maintenanceMode = maintenance;
            if (maintenance) {
                status = ElevatorStatus.MAINTENANCE;
            } else {
                status = ElevatorStatus.STOPPED;
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void addPriorityFloor(int floor) {
        if (floor >= 1 && floor <= maxFloor) {
            priorityFloors.add(floor);
        }
    }

    public int getCurrentFloor() {
        lock.lock();
        try {
            return currentFloor;
        } finally {
            lock.unlock();
        }
    }

    public Direction getDirection() {
        lock.lock();
        try {
            return direction;
        } finally {
            lock.unlock();
        }
    }

    public ElevatorStatus getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    public int getCurrentPassengers() {
        lock.lock();
        try {
            return currentPassengers;
        } finally {
            lock.unlock();
        }
    }

    public boolean isIdle() {
        lock.lock();
        try {
            return targetFloors.isEmpty() && status == ElevatorStatus.STOPPED && !emergencyMode && !maintenanceMode;
        } finally {
            lock.unlock();
        }
    }

    public boolean isAvailable() {
        lock.lock();
        try {
            return !emergencyMode && !maintenanceMode && currentPassengers < maxCapacity;
        } finally {
            lock.unlock();
        }
    }

    public int getTargetFloorsCount() {
        lock.lock();
        try {
            return targetFloors.size();
        } finally {
            lock.unlock();
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public Map<String, Object> getStatistics() {
        lock.lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalPassengers", totalPassengers);
            stats.put("totalTraveledFloors", totalTraveledFloors);
            stats.put("currentPassengers", currentPassengers);
            stats.put("idleTimeSeconds", totalIdleTime / 1000);
            stats.put("targetFloorsCount", targetFloors.size());
            stats.put("isIdle", isIdle());
            stats.put("efficiency", totalPassengers > 0 ? (double) totalTraveledFloors / totalPassengers : 0);
            return stats;
        } finally {
            lock.unlock();
        }
    }

    public String getFullInfo() {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(":\n");
            sb.append("  Этаж: ").append(currentFloor).append("\n");
            sb.append("  Статус: ").append(status).append("\n");
            sb.append("  Направление: ").append(direction).append("\n");
            sb.append("  Пассажиров: ").append(currentPassengers).append("/").append(maxCapacity).append("\n");
            sb.append("  Цели: ").append(targetFloors).append("\n");
            sb.append("  Всего перевезено: ").append(totalPassengers).append("\n");
            if (emergencyMode) sb.append("  АВАРИЙНЫЙ РЕЖИМ\n");
            if (maintenanceMode) sb.append("  ТЕХОБСЛУЖИВАНИЕ\n");
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }

    private void log(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        System.out.printf("[%s] [%s] %s%n", timestamp, name, message);
    }
}