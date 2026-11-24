.PHONY: up down test lint run-backend run-frontend eval

up:
	docker compose up -d

down:
	docker compose down

test:
	mvn -B -f backend/pom.xml verify
	cd frontend && npm ci --no-audit --no-fund && npm test && npm run build

# Compile/build checks are the available static checks for this starter; no formatter is configured yet.
lint:
	mvn -B -f backend/pom.xml -DskipTests verify
	cd frontend && npm ci --no-audit --no-fund && npm run build

eval:
	python eval/run_eval.py

run-backend:
	mvn -f backend/pom.xml spring-boot:run -pl app -am

run-frontend:
	cd frontend && npm ci --no-audit --no-fund && npm start
