import java.util.*;
import java.util.concurrent.*;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=".repeat(60));
        System.out.println("СИСТЕМА УПРАВЛЕНИЯ ЛИФТАМИ");
        System.out.println("=".repeat(60));

        System.out.print("\nВведите количество этажей: ");
        int numberOfFloors = scanner.nextInt();
        if (numberOfFloors < 2) {
            numberOfFloors = 2;
        }

        System.out.print("Введите количество лифтов: ");
        int numberOfElevators = scanner.nextInt();
        if (numberOfElevators < 1) {
            numberOfElevators = 1;
        }

        System.out.println("\nСтратегии распределения:");
        System.out.println("1. Ближайший лифт");
        System.out.println("2. Наименее загруженный");
        System.out.println("3. По направлению движения");
        System.out.println("4. Коллективное управление");
        System.out.print("Ваш выбор (1-4): ");

        int choice = scanner.nextInt();
        String strategy = switch (choice) {
            case 1 -> ElevatorController.STRATEGY_NEAREST;
            case 2 -> ElevatorController.STRATEGY_LEAST_BUSY;
            case 3 -> ElevatorController.STRATEGY_DIRECTIONAL;
            default -> ElevatorController.STRATEGY_COLLECTIVE;
        };

        System.out.print("\nВремя симуляции (секунды): ");
        int simulationDuration = scanner.nextInt();
        if (simulationDuration < 5) {
            simulationDuration = 5;
        }

        scanner.close();

        List<Elevator> elevators = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < numberOfElevators; i++) {
            int startFloor = random.nextInt(numberOfFloors) + 1;
            int capacity = 8 + random.nextInt(9);
            String name = "Лифт-" + (i + 1);
            elevators.add(new Elevator(i + 1, startFloor, numberOfFloors, capacity, name));
        }

        ElevatorController controller = new ElevatorController(elevators, numberOfFloors, strategy);

        List<Thread> elevatorThreads = new ArrayList<>();
        for (Elevator elevator : elevators) {
            Thread thread = new Thread(elevator, elevator.getName());
            thread.start();
            elevatorThreads.add(thread);
        }

        controller.start();
        Thread.sleep(1000);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("СИМУЛЯЦИЯ ЗАПУЩЕНА");
        System.out.println("Этажей: " + numberOfFloors + ", Лифтов: " + numberOfElevators);
        System.out.println("Стратегия: " + strategy + ", Время: " + simulationDuration + " сек");
        System.out.println("=".repeat(60) + "\n");

        runSimulation(controller, numberOfFloors, simulationDuration);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("ЗАВЕРШЕНИЕ СИМУЛЯЦИИ");
        System.out.println("=".repeat(60));

        controller.stop();

        for (Thread thread : elevatorThreads) {
            thread.interrupt();
        }

        for (Thread thread : elevatorThreads) {
            thread.join(1000);
        }

        controller.printDetailedStatistics();
    }

    private static void runSimulation(ElevatorController controller, int maxFloor, int durationSeconds) {
        Random random = new Random();
        long endTime = System.currentTimeMillis() + durationSeconds * 1000L;
        int requestCount = 0;

        try {
            while (System.currentTimeMillis() < endTime) {
                requestCount++;
                int fromFloor = random.nextInt(maxFloor) + 1;
                int toFloor;
                do {
                    toFloor = random.nextInt(maxFloor) + 1;
                } while (toFloor == fromFloor);

                double requestType = random.nextDouble();

                if (requestType < 0.05) {
                    controller.submitEmergencyRequest(fromFloor, toFloor);
                } else if (requestType < 0.15) {
                    controller.submitPriorityRequest(fromFloor, toFloor);
                } else {
                    controller.submitRequest(fromFloor, toFloor);
                }

                if (requestCount % 10 == 0) {
                    System.out.print(".");
                    if (requestCount % 100 == 0) {
                        long remaining = (endTime - System.currentTimeMillis()) / 1000;
                        System.out.println(" (" + requestCount + " запросов, осталось " + remaining + " сек)");
                    }
                }

                Thread.sleep(500 + random.nextInt(1000));
            }

            System.out.println("\nВсего сгенерировано запросов: " + requestCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}