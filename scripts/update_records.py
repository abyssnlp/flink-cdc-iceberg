import psycopg2
import time
import logging
from datetime import datetime
from typing import Dict, Any, Optional

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


class PostgresUpdater:
    def __init__(
            self,
            dbname: str,
            user: str,
            password: str,
            host: str = "localhost",
            port: int = 5432,
    ):
        self.dbname = dbname
        self.user = user
        self.password = password
        self.host = host
        self.port = port
        self.conn = None
        self.cursor = None

    def connect(self) -> None:
        try:
            self.conn = psycopg2.connect(
                dbname=self.dbname,
                user=self.user,
                password=self.password,
                host=self.host,
                port=self.port,
            )
            self.cursor = self.conn.cursor()
            logger.info("Connected to the database")
        except Exception as e:
            logger.error(f"Error connecting to the database: {e}")
            raise

    def disconnect(self) -> None:
        if self.cursor:
            self.cursor.close()
        if self.conn:
            self.conn.close()
        logger.info("Disconnected from the database")

    def update_records(
            self, table: str, update_record: Dict[str, Any], where: Optional[str] = None
    ) -> int:
        if not self.conn or self.conn.closed:
            self.connect()

        try:
            set_clause = ", ".join([f"{k} = %s" for k in update_record.keys()])
            where_clause = f"WHERE {where}" if where else ""
            query = f"UPDATE {table} SET {set_clause} {where_clause}"
            values = list(update_record.values())
            self.cursor.execute(query, values)
            self.conn.commit()
            return self.cursor.rowcount
        except Exception as e:
            logger.error(f"Error updating records: {e}")
            self.conn.rollback()
            raise


if __name__ == "__main__":
    import os
    import faker

    fake = faker.Faker()
    updater = PostgresUpdater(
        dbname=os.getenv("POSTGRES_DB", "cdciceberg"),
        user=os.getenv("POSTGRES_USER", "postgres"),
        password=os.getenv("POSTGRES_PASSWORD", "postgres"),
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=os.getenv("POSTGRES_PORT", 5432),
    )

    for i in range(100):
        update_record = {
            "address": fake.address(),
        }
        where = f"id = {i + 1}"
        updated_rows = updater.update_records("customers", update_record, where)
        logger.info(f"Updated {updated_rows} rows in the customers table")
        time.sleep(1)
