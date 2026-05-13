# SQL to MongoDB translator

# Описание

Веб-приложение, которое осуществляет разбор SQL-запроса и переводит его в MongoDB формат.
Разбор состоит из 4 фаз:
1. Лексический анализ SQL-запроса.
     
   Выполняется деление всего текста на слова, при котором выполняется попытка определения типа каждого слова.
   Если тип определить не удаётся, то слово помечается как нераспознанное.  
   В результате этого этапа получается список токенов (пар слово и его тип).
   
2. Синтаксический анализ SQL-запроса.
    
   Выполняется проверка допустимости конструкций, предложенных в запросе, с точки зрения языка SQL.  
   В результате этого этапа получается дерево разбора SQL-запроса.
   
3. Формирование промежуточного представления для перевода в MongoDB формат.
    
   Дерево разбора с предыдущего щага преобразуется в формат, удобный для выполнения перевода на язык MongoDB.  
   В результате этого этапа получается дерево разбора SQL-запроса.
   
4. Генератор кода на языке MongoDB.
   
   На основе промежуточного представления создаётся код запроса для MongoDB.
---
# Используемые технологии
* Java
* Spring Boot
* Gradle
* PostgreSQL
* MongoDB
* RabbitMQ
* Docker
* React
---
# Пример работы
![image](https://github.com/user-attachments/assets/d59414ff-c719-4b52-a2cc-ee1834d3eb7e)
![image](https://github.com/user-attachments/assets/424d472e-6617-4e13-a833-bf36ca859360)
![image](https://github.com/user-attachments/assets/ca96676b-7a9a-45e2-a481-7445d3547c02)
![image](https://github.com/user-attachments/assets/f73b8714-1371-4fcf-8b4f-36ee19bb72d9)
![image](https://github.com/user-attachments/assets/796c0732-a3dd-4b14-9a15-814cf331058c)
![image](https://github.com/user-attachments/assets/9a998793-cf11-45e7-b03b-ccd9051f38f6)
<img width="1917" height="950" alt="IR_DEMO_1" src="https://github.com/user-attachments/assets/07b88eda-280f-41c3-85af-48648ad06e19" />
<img width="1917" height="951" alt="IR_DEMO_2" src="https://github.com/user-attachments/assets/b8c16e07-7fda-4321-97b5-11261e061ecc" />
<img width="1916" height="956" alt="CODE_GEN_1" src="https://github.com/user-attachments/assets/13f50a59-b8ad-4940-a1f1-e88675c7a179" />








