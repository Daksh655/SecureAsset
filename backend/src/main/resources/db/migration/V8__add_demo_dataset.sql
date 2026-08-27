CREATE TABLE demo_datasets (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL
);

ALTER TABLE customers ADD COLUMN dataset_id UUID REFERENCES demo_datasets(id);
CREATE INDEX idx_customers_dataset_id ON customers(dataset_id);
