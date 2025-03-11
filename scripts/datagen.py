from faker import Faker
import datetime
import random

# Initialize Faker
fake = Faker()

# Create SQL file
with open('sql/customer_data.sql', 'w') as f:
    # Write create table statement
    f.write("""
CREATE TABLE IF NOT EXISTS customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    date_of_joining DATE,
    updated_at TIMESTAMP,
    address TEXT
);

""")

    # Generate insert statements
    insert_template = "INSERT INTO customers (name, date_of_joining, updated_at, address) VALUES "
    batch_size = 1000
    values = []

    for i in range(10000):
        name = fake.name()
        doj = fake.date_between(start_date='-5y', end_date='today')
        updated_at = fake.date_time_between(start_date=doj)
        address = fake.address().replace('\n', ', ')

        value = f"('{name}', '{doj}', '{updated_at}', '{address}')"
        values.append(value)

        if len(values) == batch_size or i == 9999:
            f.write(insert_template + ',\n'.join(values) + ';\n')
            values = []
