import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ElevatorController implements Runnable {
    private final List<Elevator> elevators;
    private final PriorityBlockingQueue<PassengerRequest> requestQueue;
    private final int maxFloor;
    private volatile boolean running = true;
    private final AtomicInteger requestsProcessed = new AtomicInteger(0);
    private final AtomicInteger requestsRejected = new AtomicInteger(0);
    private final Map<Integer, Long> requestProcessingTimes = new ConcurrentHashMap<>();
    private final Thread controllerThread;
    private final String strategy;

    public static final String STRATEGY_NEAREST = "NEAREST";
    public static final String STRATEGY_LEAST_BUSY = "LEAST_BUSY";
    public static final String STRATEGY_DIRECTIONAL = "DIRECTIONAL";
    public static final String STRATEGY_COLLECTIVE = "COLLECTIVE";

    public ElevatorController(List<Elevator> elevators, int maxFloor, String strategy) {
        this.elevators = new ArrayList<>(elevators);
        this.maxFloor = maxFloor;
        this.strategy = strategy != null ? strategy : STRATEGY_COLLECTIVE;
        this.requestQueue = new PriorityBlockingQueue<>();
        this.controllerThread = new Thread(this, "ElevatorController");
    }

    @Override
    public void run() {
        while (running || !requestQueue.isEmpty()) {
            try {
                PassengerRequest request = requestQueue.poll(100, TimeUnit.MILLISECONDS);
                if (request != null) {
                    long startTime = System.currentTimeMillis();
                    boolean processed = processRequest(request);
                    long endTime = System.currentTimeMillis();

                    requestProcessingTimes.put(request.getId(), endTime - startTime);

                    if (processed) {
                        requestsProcessed.incrementAndGet();
                    } else {
                        requestsRejected.incrementAndGet();
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean processRequest(PassengerRequest request) {
        log("Обработка " + request);

        if (!isValidRequest(request)) {
            return false;
        }

        Elevator selectedElevator = selectElevator(request);

        if (selectedElevator != null) {
            selectedElevator.addRequest(request);
            log("Назначен " + selectedElevator.getName() + " для " + request);
            return true;
        } else {
            return false;
        }
    }

    private boolean isValidRequest(PassengerRequest request) {
        if (request.getFloor() < 1 || request.getFloor() > maxFloor) {
            return false;
        }

        if (request.getTargetFloor() < 1 || request.getTargetFloor() > maxFloor) {
            return false;
        }

        if (request.getFloor() == request.getTargetFloor()) {
            return false;
        }

        return true;
    }

    private Elevator selectElevator(PassengerRequest request) {
        switch (strategy) {
            case STRATEGY_NEAREST:
                return selectNearestElevator(request);
            case STRATEGY_LEAST_BUSY:
                return selectLeastBusyElevator(request);
            case STRATEGY_DIRECTIONAL:
                return selectDirectionalElevator(request);
            case STRATEGY_COLLECTIVE:
            default:
                return selectCollectiveControlElevator(request);
        }
    }

    private Elevator selectNearestElevator(PassengerRequest request) {
        Elevator bestElevator = null;
        int minDistance = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (!elevator.isAvailable()) continue;

            int distance = Math.abs(elevator.getCurrentFloor() - request.getFloor());
            if (distance < minDistance) {
                minDistance = distance;
                bestElevator = elevator;
            }
        }

        return bestElevator;
    }

    private Elevator selectLeastBusyElevator(PassengerRequest request) {
        Elevator bestElevator = null;
        double minLoad = Double.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (!elevator.isAvailable()) continue;

            double load = (double) elevator.getCurrentPassengers() / elevator.getMaxCapacity();
            load += elevator.getTargetFloorsCount() * 0.1;

            if (load < minLoad) {
                minLoad = load;
                bestElevator = elevator;
            }
        }

        return bestElevator;
    }

    private Elevator selectDirectionalElevator(PassengerRequest request) {
        Elevator bestElevator = null;
        int bestScore = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (!elevator.isAvailable()) continue;

            int score = calculateDirectionalScore(elevator, request);
            if (score < bestScore) {
                bestScore = score;
                bestElevator = elevator;
            }
        }

        return bestElevator;
    }

    private Elevator selectCollectiveControlElevator(PassengerRequest request) {
        Elevator bestElevator = null;
        double bestScore = Double.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (!elevator.isAvailable()) continue;

            double score = calculateCollectiveScore(elevator, request);
            if (score < bestScore) {
                bestScore = score;
                bestElevator = elevator;
            }
        }

        return bestElevator;
    }

    private int calculateDirectionalScore(Elevator elevator, PassengerRequest request) {
        int distance = Math.abs(elevator.getCurrentFloor() - request.getFloor());
        int score = distance;

        Direction elevatorDir = elevator.getDirection();
        Direction requestDir = request.getDirection();

        if (elevator.isIdle()) {
            score -= 5;
        } else if (elevatorDir == requestDir) {
            if ((elevatorDir == Direction.UP && request.getFloor() >= elevator.getCurrentFloor()) ||
                    (elevatorDir == Direction.DOWN && request.getFloor() <= elevator.getCurrentFloor())) {
                score -= 3;
            } else {
                score += 5;
            }
        } else {
            score += 10;
        }

        score += elevator.getTargetFloorsCount() * 2;

        return Math.max(0, score);
    }

    private double calculateCollectiveScore(Elevator elevator, PassengerRequest request) {
        double score = 0;
        double passengerWaitTime = estimatePassengerWaitTime(elevator, request);
        score += passengerWaitTime * 0.5;
        double additionalTravelTime = estimateAdditionalTravelTime(elevator, request);
        score += additionalTravelTime;
        double energyEfficiency = estimateEnergyImpact(elevator, request);
        score += energyEfficiency * 0.1;
        score -= request.getPriority() * 0.2;
        double capacityRatio = (double) elevator.getCurrentPassengers() / elevator.getMaxCapacity();
        if (capacityRatio > 0.8) {
            score += 10;
        }
        return score;
    }

    private double estimatePassengerWaitTime(Elevator elevator, PassengerRequest request) {
        int distance = Math.abs(elevator.getCurrentFloor() - request.getFloor());
        double travelTime = distance;
        double stopTime = elevator.getTargetFloorsCount() * 3;
        return travelTime + stopTime;
    }

    private double estimateAdditionalTravelTime(Elevator elevator, PassengerRequest request) {
        double additionalTime = 0;
        int currentPassengers = elevator.getCurrentPassengers();
        if (currentPassengers > 0) {
            int detourEstimate = Math.abs(request.getFloor() - elevator.getCurrentFloor()) +
                    Math.abs(request.getTargetFloor() - request.getFloor());
            additionalTime = detourEstimate * currentPassengers * 0.5;
        }
        return additionalTime;
    }

    private double estimateEnergyImpact(Elevator elevator, PassengerRequest request) {
        double efficiency = 0;
        int passengers = elevator.getCurrentPassengers();
        if (passengers < elevator.getMaxCapacity() / 2) {
            efficiency = (elevator.getMaxCapacity() / 2 - passengers) * 0.5;
        }
        return efficiency;
    }

    public void start() {
        controllerThread.start();
    }

    public void stop() {
        running = false;
        try {
            controllerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void submitRequest(PassengerRequest request) {
        requestQueue.offer(request);
    }

    public void submitRequest(int fromFloor, int toFloor) {
        Direction direction = (toFloor > fromFloor) ? Direction.UP : Direction.DOWN;
        PassengerRequest request = new PassengerRequest(fromFloor, direction, toFloor);
        submitRequest(request);
    }

    public void submitEmergencyRequest(int fromFloor, int toFloor) {
        Direction direction = (toFloor > fromFloor) ? Direction.UP : Direction.DOWN;
        PassengerRequest request = new PassengerRequest(fromFloor, direction, toFloor, RequestType.EMERGENCY);
        submitRequest(request);
    }

    public void submitPriorityRequest(int fromFloor, int toFloor) {
        Direction direction = (toFloor > fromFloor) ? Direction.UP : Direction.DOWN;
        PassengerRequest request = new PassengerRequest(fromFloor, direction, toFloor, RequestType.PRIORITY);
        submitRequest(request);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", requestsProcessed.get() + requestsRejected.get());
        stats.put("processedRequests", requestsProcessed.get());
        stats.put("rejectedRequests", requestsRejected.get());
        stats.put("pendingRequests", requestQueue.size());
        stats.put("strategy", strategy);

        if (!requestProcessingTimes.isEmpty()) {
            double avgTime = requestProcessingTimes.values().stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            stats.put("averageProcessingTimeMs", avgTime);
        }

        List<Map<String, Object>> elevatorStats = new ArrayList<>();
        for (Elevator elevator : elevators) {
            Map<String, Object> elevatorInfo = new HashMap<>();
            elevatorInfo.put("id", elevator.getId());
            elevatorInfo.put("name", elevator.getName());
            elevatorInfo.put("status", elevator.getStatus());
            elevatorInfo.put("currentFloor", elevator.getCurrentFloor());
            elevatorInfo.put("passengers", elevator.getCurrentPassengers());
            elevatorInfo.put("statistics", elevator.getStatistics());
            elevatorStats.add(elevatorInfo);
        }
        stats.put("elevators", elevatorStats);

        return stats;
    }

    public void printStatus() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("СТАТУС СИСТЕМЫ УПРАВЛЕНИЯ ЛИФТАМИ");
        System.out.println("=".repeat(60));
        System.out.printf("Стратегия распределения: %s%n", strategy);
        System.out.printf("Обработано запросов: %d, Отклонено: %d, В очереди: %d%n",
                requestsProcessed.get(), requestsRejected.get(), requestQueue.size());
        System.out.println("\nЛИФТЫ:");
        for (Elevator elevator : elevators) {
            System.out.println(elevator.getFullInfo());
        }
        System.out.println("=".repeat(60) + "\n");
    }

    public void printDetailedStatistics() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ДЕТАЛЬНАЯ СТАТИСТИКА");
        System.out.println("=".repeat(60));
        Map<String, Object> stats = getStatistics();
        System.out.printf("Всего запросов: %d%n", stats.get("totalRequests"));
        System.out.printf("Успешно обработано: %d%n", stats.get("processedRequests"));
        System.out.printf("Отклонено: %d%n", stats.get("rejectedRequests"));
        System.out.printf("В очереди: %d%n", stats.get("pendingRequests"));
        if (stats.containsKey("averageProcessingTimeMs")) {
            System.out.printf("Среднее время обработки: %.2f мс%n", stats.get("averageProcessingTimeMs"));
        }
        System.out.println("\nСТАТИСТИКА ПО ЛИФТАМ:");
        List<Map<String, Object>> elevatorStats = (List<Map<String, Object>>) stats.get("elevators");
        for (Map<String, Object> elevatorInfo : elevatorStats) {
            System.out.printf("%n%s (ID: %d):%n", elevatorInfo.get("name"), elevatorInfo.get("id"));
            System.out.printf("  Этаж: %d, Статус: %s, Пассажиров: %d%n",
                    elevatorInfo.get("currentFloor"), elevatorInfo.get("status"), elevatorInfo.get("passengers"));
            Map<String, Object> elevStats = (Map<String, Object>) elevatorInfo.get("statistics");
            System.out.printf("  Всего перевезено: %d%n", elevStats.get("totalPassengers"));
            System.out.printf("  Проехано этажей: %d%n", elevStats.get("totalTraveledFloors"));
            System.out.printf("  Время простоя: %d сек%n", elevStats.get("idleTimeSeconds"));
            System.out.printf("  Эффективность: %.2f этажей/пассажира%n", elevStats.get("efficiency"));
        }
        System.out.println("=".repeat(60) + "\n");
    }

    private void log(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        System.out.printf("[%s] [Диспетчер] %s%n", timestamp, message);
    }
}