package fr.uge.corp.ifscars.renting;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.corp.ifscars.cars.ICar;
import fr.uge.corp.ifscars.cars.Storage;
import fr.uge.corp.ifscars.observe.IObservateur;

@SuppressWarnings("serial")
public class RentingService extends UnicastRemoteObject implements IRentingService {
	private final Storage storage;
	private final Map<String, Queue<Long>> rentingQueue;
	private final List<IObservateur> observateurs;
	private static final Logger logger = Logger.getLogger(RentingService.class.getName());

	public RentingService(Storage storage) throws RemoteException {
		Objects.requireNonNull(storage);
		this.storage = storage;
		rentingQueue = new HashMap<>();
		observateurs = new ArrayList<>();
	}

	@Override
	public String display() throws RemoteException {
		return storage.display();
	}
	
	private ICar giveCar(long id, String model, boolean condition) throws RemoteException {
		if (!condition) {
			return ICar.NULL_CAR;
		}
		Queue<Long> queue = rentingQueue.computeIfAbsent(model, __ -> new LinkedBlockingQueue<>());
		if (queue.size() > 0) {
			if (id == queue.peek()) {
				queue.poll();
				return storage.get(model);
			} else {
				return ICar.NULL_CAR;
			}
		}
		return storage.get(model);
	}
	
	private ICar waitCar(long id, String model, boolean condition) {
		if (!condition) {
			return ICar.NULL_CAR;
		}
		Queue<Long> queue = rentingQueue.computeIfAbsent(model, __ -> new LinkedBlockingQueue<>());
		if (!queue.contains(id)) {
			queue.offer(id);
			logger.log(Level.INFO, queue.toString());
		}
		
		return ICar.NULL_CAR;
	}
	
	@Override
	public void notifyObservateurs() throws RemoteException {
		for (IObservateur obs : observateurs) {
			obs.notifyChange(this);
		}
	}
	
	@Override
	public ICar getCar(long id, String model, boolean condition) throws RemoteException {
		Objects.requireNonNull(model);
		ICar car;
		switch (getStatus(model)) {
			case Give: 
				car = giveCar(id, model, condition);
				break;
			case Wait: 
				car = waitCar(id, model, condition);
				break;
			case None: default: 
				car =  ICar.NULL_CAR;
				break;
		}
		//notifyObservateurs();
		return car;
		
	}

	@Override
	public double getCarPrice(String model) throws RemoteException {
		ICar car = storage.get(model);
		if (car.isNull()) {
			return 0;
		}
		return car.getPrice() / 2;
	}


	@Override
	public void returnCar(ICar car) throws RemoteException {
		Objects.requireNonNull(car);
		if (car.isNull()) {
			return;
		}
		storage.add(car, 1);
		//notifyObservateurs();

	}

	@Override
	public RentStatus getStatus(String model) throws RemoteException {
		ICar car = storage.get(model);
		
		if (!storage.exists(model)) {
			return RentStatus.None;
		} else if (car.isNull()) {
			return RentStatus.Wait;
		} else {
			storage.add(car, 1);
			return RentStatus.Give;
		}
	}

	@Override
	public synchronized void subscribe(IObservateur obs) throws RemoteException {
		Objects.requireNonNull(obs);
		observateurs.add(obs);
		
	}

}