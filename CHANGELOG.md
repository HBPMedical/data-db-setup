
# Changelog

## 2.5.0 - 2018-09-21

* Use datackages.json (see [Frictionlessdata](https://http://frictionlessdata.io/)) to define the data to load.

## 2.4.0 - 2018-09-07

* Configuration is now stored in folder /flyway/config/ inside the Docker image
* Update flyway to 5.1.4

## 2.3.0 - 2018-07-30

* Support new column types: text, date, timestamp
* Allow loading no data for a dataset, by setting property \_\_CSV_FILE to /dev/null value

## 2.2.0 - 2018-05-02

* Support generating views but no datasets.

## 2.1.0 - 2017-08-10

* Support multiple tables and datasets
* Generate views

## 2.0.0 - 2017-07-21

* Support multiple datasets

## 1.0.0 - 2017-05-11

* First stable release
