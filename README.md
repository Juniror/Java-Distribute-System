# Java Heartbeat with Redis

โปรเจกต์นี้เป็นงานเกี่ยวกับ **การส่ง Heartbeat ระหว่าง Service/Process ที่เขียนด้วยภาษา Java**  
โดยใช้ **Redis** เป็นตัวกลางในการเก็บสถานะ (State) และตรวจสอบความพร้อมใช้งานของแต่ละ Node

## แนวคิดของระบบ (Concept)

ในระบบที่มีหลาย Process หรือ Distributed System  
จำเป็นต้องมีวิธีตรวจสอบว่าแต่ละ Service ยังทำงานอยู่หรือไม่

โปรเจกต์นี้ใช้แนวคิด **Heartbeat** คือ  
- แต่ละ Service จะส่งสัญญาณ (Heartbeat) ไปที่ Redis เป็นระยะ ๆ  และ Redis จะส่งให้ตัวที่เหลือ
- หากไม่มี Heartbeat ภายในเวลาที่กำหนด จะถือว่า Service นั้น **Dead**
- หาก Dead ตัวหมายเลข PID สูงสุดจะได้กลายเป็น Boss แทน 

## เทคโนโลยีที่ใช้ (Tech Stack)

- Java  
- Redis  
- แนวคิด Distributed Process

