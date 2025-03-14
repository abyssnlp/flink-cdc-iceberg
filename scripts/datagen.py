import os
import psycopg2
from faker import Faker
import datetime
import random
import logging
from typing import List, Tuple
import time

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("postgres_operations")


class PostgresOperator:
    def __init__(self, dbname: str, user: str, password: str, host: str = 'localhost', port: int = 5432):
        """Initialize the PostgreSQL operator with connection parameters"""
        self.conn_params = {
            'dbname': dbname,
            'user': user,
            'password': password,
            'host': host,
            'port': port
        }
        self.conn = None
        self.cursor = None

    def connect(self) -> None:
        """Establish connection to the PostgreSQL database"""
        try:
            logger.info("Connecting to PostgreSQL database...")
            self.conn = psycopg2.connect(**self.conn_params)
            self.cursor = self.conn.cursor()
            logger.info("Successfully connected to PostgreSQL database")
        except psycopg2.Error as e:
            logger.error(f"Error connecting to PostgreSQL database: {e}")
            raise

    def disconnect(self) -> None:
        """Close the database connection"""
        try:
            if self.cursor:
                self.cursor.close()
            if self.conn:
                self.conn.close()
                logger.info("Database connection closed")
        except psycopg2.Error as e:
            logger.error(f"Error disconnecting from database: {e}")

    def execute_query(self, query: str, params: tuple = None) -> None:
        """Execute a query with optional parameters"""
        if not self.conn or self.conn.closed:
            self.connect()

        try:
            start_time = time.time()
            self.cursor.execute(query, params)
            self.conn.commit()
            execution_time = time.time() - start_time
            affected_rows = self.cursor.rowcount if self.cursor.rowcount >= 0 else 0
            logger.info(f"Query executed successfully in {execution_time:.2f}s. Rows affected: {affected_rows}")
        except psycopg2.Error as e:
            self.conn.rollback()
            logger.error(f"Error executing query: {e}")
            logger.error(f"Query was: {query[:100]}...")
            raise

    def create_table(self) -> None:
        """Create the customers table if it doesn't exist"""
        create_table_sql = """
        CREATE TABLE IF NOT EXISTS customers (
            id SERIAL PRIMARY KEY,
            name VARCHAR(100),
            date_of_joining DATE,
            updated_at TIMESTAMP,
            address TEXT
        )
        """
        logger.info("Creating customers table if it doesn't exist...")
        self.execute_query(create_table_sql)
        logger.info("Table creation complete")

    def insert_batch(self, values: List[Tuple]) -> None:
        """Insert a batch of values into the customers table"""
        if not values:
            return

        insert_sql = """
        INSERT INTO customers (name, date_of_joining, updated_at, address) 
        VALUES %s
        """
        args_str = ','.join(self.cursor.mogrify("(%s,%s,%s,%s)", v).decode('utf-8') for v in values)
        self.execute_query(insert_sql.replace("%s", args_str))

    def generate_and_insert_data(self, total_records: int, batch_size: int) -> None:
        """Generate fake data and insert it into the database in batches"""
        fake = Faker()
        logger.info(f"Generating and inserting {total_records} customer records with batch size {batch_size}...")

        batches_count = (total_records + batch_size - 1) // batch_size
        records_inserted = 0

        for batch_num in range(batches_count):
            batch_records = min(batch_size, total_records - records_inserted)
            values = []

            for _ in range(batch_records):
                name = fake.name()
                doj = fake.date_between(start_date='-5y', end_date='today')
                updated_at = fake.date_time_between(start_date=doj)
                address = fake.address().replace('\n', ', ')

                values.append((name, doj, updated_at, address))

            try:
                start_time = time.time()
                self.insert_batch(values)
                batch_time = time.time() - start_time
                records_inserted += len(values)
                logger.info(
                    f"Batch {batch_num + 1}/{batches_count} completed: {len(values)} records inserted in {batch_time:.2f}s. Total progress: {records_inserted}/{total_records}")
            except Exception as e:
                logger.error(f"Error in batch {batch_num + 1}: {e}")
                raise

        logger.info(f"Data generation and insertion complete. Total {records_inserted} records inserted.")


def main():
    db_params = {
        'dbname': 'cdciceberg',
        'user': 'postgres',
        'password': 'postgres',
        'host': 'localhost',
        'port': 5432
    }

    # Configuration
    total_records = 10000
    batch_size = 1000

    logger.info("Starting PostgreSQL data population process")

    try:
        pg_operator = PostgresOperator(**db_params)
        pg_operator.connect()

        pg_operator.create_table()
        pg_operator.generate_and_insert_data(total_records, batch_size)

    except Exception as e:
        logger.error(f"Fatal error: {e}")
    finally:
        if 'pg_operator' in locals():
            pg_operator.disconnect()

    logger.info("Process complete")


if __name__ == "__main__":
    main()
