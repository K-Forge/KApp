# K-APP

## Mobile Application for Fundación Universitaria Konrad Lorenz

### Software Requirements Specification (SRS)

**Version:** 1.0
**Date:** 00/00/2025

---

> Any form of exploitation is prohibited, in particular the reproduction, distribution, public communication and/or
> transformation, in whole or in part, by any means, of this document without the prior express written consent of
> Brian Steven Vargas Clavijo and Santiago Rocha Ramírez.

---

## Version Control

| Version | Reason for Change | Owner | Date       |
| ------- | ----------------- | ----- | ---------- |
| 1.0     | Initial version   |       | 00/00/2025 |

---

## Table of Contents

- [1.1 Purpose](#11-purpose)
- [1.2 Objectives](#12-objectives)
  - [1.2.1 General Objective](#121-general-objective)
  - [1.2.2 Specific Objectives](#122-specific-objectives)
- [1.3 Scope](#13-scope)
- [1.4 Definitions, Acronyms and Abbreviations](#14-definitions-acronyms-and-abbreviations)
- [2.1 Product Perspective](#21-product-perspective)
- [2.2 Product Features (Summary)](#22-product-features-summary)
- [2.3 User Characteristics](#23-user-characteristics)
- [2.4 General Constraints](#24-general-constraints)
- [2.5 Assumptions and Dependencies](#25-assumptions-and-dependencies)
- [3.1 Functional Requirements](#31-functional-requirements)
- [3.2 Non-Functional Requirements](#32-non-functional-requirements)
- [3.3 User Roles and Permissions](#33-user-roles-and-permissions)

---

## 1. Introduction

### 1.1 Purpose

K-APP is a mobile application created within the **K-Forge** development club, aimed at students of Fundación
Universitaria Konrad Lorenz. Its purpose is to centralize in one place the most relevant information and services of
university life, providing fast access to academic, administrative and communication features from an intuitive and
secure environment.

### 1.2 Objectives

#### 1.2.1 General Objective

Develop a mobile application that simplifies the university experience by integrating academic, administrative,
communication and student-participation tools in a single place.

#### 1.2.2 Specific Objectives

1. Facilitate academic management through quick access to schedules, grades and relevant notifications.
2. Improve communication among students and with the university through messaging and real-time announcements.
3. Centralize digital services such as the university ID card, space reservations and institutional appointments to
   simplify administrative procedures.
4. Foster a sense of university community by promoting and enabling registration for campus events and activities.

### 1.3 Scope

K-APP will be available as a mobile application for Android and iOS devices, aimed primarily at students of
Fundación Universitaria Konrad Lorenz, while also allowing limited access to professors, administrative staff and
guests.

The application will integrate in one place:

- **Academic features:** schedules, grades and notifications.
- **University services:** digital ID card, space reservations, wellbeing appointments and tutoring.
- **Communication:** private and group chat, plus real-time official announcements.
- **Institutional information:** interactive map, event calendar and social networks.

Development will focus on delivering an **intuitive, secure and customizable experience**, ensuring students can
manage their university life from anywhere. Integration with the university's existing systems will be prioritized
to keep information up to date without duplicating processes.

### 1.4 Definitions, Acronyms and Abbreviations

| Term                        | Definition                                                                                                                                                                     |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **K-APP**                   | Mobile application built for Fundación Universitaria Konrad Lorenz to centralize services and tools for students, professors, administrative staff and guests.                   |
| **K-Forge**                 | Student technology development club responsible for designing and maintaining K-APP and other software projects for the university.                                              |
| **University credentials**  | Username and password issued by the university to identify each member and grant access to internal systems.                                                                     |
| **Digital ID card**         | Electronic version of the university ID card available inside K-APP, including personal information and a QR code for validation.                                                |
| **QR code**                 | Graphical code scanned with the device camera to authenticate identity or access services.                                                                                       |
| **User roles**              | Profiles with different access levels and permissions: Student, Professor, Administrator, Administrative Staff and Guest.                                                        |
| **Internal chat**           | In-app messaging feature for private or group communication among members of the university community.                                                                           |
| **University services**     | Set of features that support procedures or reservations: wellbeing appointments, tutoring, parking and academic support.                                                          |
| **Events**                  | Academic, cultural, sports or institutional activities organized by the university.                                                                                              |
| **Interactive map**         | Visual tool showing buildings, classrooms and relevant campus spaces.                                                                                                            |
| **Push notifications**      | Real-time alerts sent to the mobile device about events, changes or important messages.                                                                                          |
| **LMS**                     | Learning Management System: digital platform for managing learning (such as Moodle or Blackboard).                                                                               |
| **System integration**      | Connection between K-APP and the university's existing platforms to synchronize data.                                                                                            |
| **Secure authentication**   | Process that verifies user identity through passwords, verification codes or two-factor authentication.                                                                          |
| **Accessibility**           | Design characteristics that make the app usable by people with disabilities (enlarged text, high contrast, and so on).                                                           |
| **Customization**           | Ability to adjust the appearance or configuration of the app (colors, themes, font sizes).                                                                                       |
| **API**                     | Application Programming Interface: set of rules and methods that let different systems communicate with each other.                                                               |
| **App**                     | Application designed to run on mobile devices.                                                                                                                                   |

---

## 2. General Description

### 2.1 Product Perspective

K-APP is a standalone mobile application that will integrate with the existing academic and administrative systems of
Fundación Universitaria Konrad Lorenz. It will act as a centralized platform for students, professors,
administrative staff and guests, granting access to information and services without needing multiple applications or
web portals.

### 2.2 Product Features (Summary)

- Secure authentication with university credentials, or limited guest access.
- Access to schedules, grades and academic history.
- Communication through private and group chat with real-time notifications.
- Digital university ID card with a unique and secure QR code.
- Access to services such as space reservations, wellbeing appointments, tutoring and parking.
- University event information and direct registration from the app.
- Interactive campus map and guided orientation.
- Display of official posts and social networks.
- Built-in support center and feedback channel.
- Accessibility options and interface customization.

### 2.3 User Characteristics

| Role                     | Description                                                                            |
| ------------------------ | -------------------------------------------------------------------------------------- |
| **Students**             | Primary users with full access to academic tools and university services.               |
| **Professors**           | Access to class management, communication with students and grade publication.          |
| **Administrators**       | Full control to manage users, events and system configuration.                          |
| **Administrative staff** | Access to appointment, event and resource management without modifying academic data.   |
| **Guests**               | Limited access to events and public content.                                            |

### 2.4 General Constraints

- The application must be available for Android and iOS.
- Full access requires valid university credentials.
- All sensitive information must be handled with encryption and comply with data protection regulations.
- Integration with current systems depends on the availability of APIs provided by the university.

### 2.5 Assumptions and Dependencies

- Users will have an Internet connection to access most features.
- The university will provide the databases and services required to synchronize academic and administrative data.
- Real-time notifications require devices to allow push alerts.
- Successful rollout depends on proper collaboration between K-Forge, the university and the existing system
  vendors.

---

## 3. Specific Requirements

### 3.1 Functional Requirements

#### 3.1.1 Authentication and Access

1. The application must allow login using university credentials (student/professor code and password).
2. The application must allow guest access with features limited to public information.
3. The application must offer the option to remember credentials to speed up future logins.
4. The application must allow password recovery through a secure validation process.

#### 3.1.2 General Navigation

5. The application must include a home button accessible from any screen to return to the main page.
6. The application must have a structured navigation menu for quick access to all main sections.

#### 3.1.3 Communication

7. The application must allow communication among students through a private and group chat system.
8. The chat system must support sending text, images, videos and file attachments.
9. The application must send real-time push notifications for important announcements, new messages and relevant
   changes.

#### 3.1.4 Academic Management

10. The application must allow viewing the current academic schedule.
11. The application must notify the user about class changes or cancellations.
12. The application must allow viewing grades for the current semester.
13. The application must allow access to the grade history of previous semesters.

#### 3.1.5 Digital University ID Card

14. The application must display the university ID card in digital format.
15. The system must generate a unique and secure QR code bound to the student's identity.

#### 3.1.6 Library

16. The application must display the personal book loan history and return dates.
17. The application must allow renewing loans directly from the app.
18. The application must allow reserving available books or joining a waiting list.

#### 3.1.7 University Services

19. The application must allow reserving spaces or appointments at Student Wellbeing.
20. The application must allow managing the reservation and access to university parking, including payment.
21. The application must allow booking academic tutoring sessions.
22. The application must allow scheduling appointments with academic coordination.
23. The application must allow scheduling appointments with the psychology service.

#### 3.1.8 Events and Activities

24. The application must display upcoming events organized by the university.
25. The application must allow filtering events by category (cultural, academic, sports, and so on).
26. The application must allow registering directly for university events.
27. The application must allow adding events to the user's personal calendar.

#### 3.1.9 Location and Orientation

28. The application must display an interactive map of the university campus.
29. The application must show the location of classrooms, buildings and relevant spaces.
30. The application must offer interactive guides or videos to orient the user within the campus.

#### 3.1.10 Social Networks

31. The application must redirect users to the university's official social networks.
32. The application must display recent university posts or updates inside the app.

### 3.2 Non-Functional Requirements

1. The application must be available for Android and iOS devices.
2. All sensitive information must be handled with encryption and comply with data protection regulations.
3. The application must guarantee response times under 2 seconds under normal usage conditions.
4. The application must be accessible, including high contrast, enlarged text and screen reader compatibility.
5. The application must allow interface customization (themes, font sizes, colors).

### 3.3 User Roles and Permissions

#### 3.3.1 Student

- Full access to academic tools, university services, chat, digital ID card, interactive map and events.
- May book tutoring sessions, spaces and institutional appointments.
- Receives personalized notifications.

#### 3.3.2 Professor

- Access to class, schedule and grade management.
- May communicate with students through chat and notifications.
- May schedule academic tutoring sessions and create academic events.

#### 3.3.3 Administrator

- Full control over the application: user, role, event and configuration management.
- Access to reports and usage history.
- May modify academic and administrative information.

#### 3.3.4 Administrative Staff

- Access to service and institutional appointment management.
- May monitor resource availability and manage general events.

#### 3.3.5 Guest

- Limited access to public information, open events and official social networks.

---

> IMPORTANT: **TODO:** sections 4 to 10 are being reformatted and restructured for clarity.

---

3. Main Features
1. Authentication and Access
   Login with university credentials: allow students to access the app using their student number (student code) and
   the associated password.
   Guest login: limited access to the university's public features without authentication.
   Remember login data: option to store the user's credentials to log in automatically on future visits.
   Password recovery: feature to recover the password if forgotten, through a secure validation process.
1. General Navigation
   Home button: fast and easy access to the app home screen from anywhere.
   Intuitive navigation menu: a clear, well-structured menu for quick access to every section of the application
   (schedules, grades, services, and so on).
1. Communication
   Chat system: allow communication among students through private or group chat.
   Push notifications: send real-time notifications for important events, new messages or changes in the academic
   schedule.
1. Academic Management
   Schedule view: easy access to the current academic schedule, with the option to see details of each class,
   classroom and professor.
   Schedule change notifications: alerts about any change, cancellation or rescheduling of classes.
   Grade view: access grades for the current semester, with the ability to review the grade history of previous
   semesters.
1. Virtual ID Card
   Digital university ID card view: display the university ID card digitally (including a QR code bound to the
   student's identity).
   QR code generation: create a unique QR code associated with the student's identity, to be scanned across
   different services within the university
1. Library
   Access to the library record: view loan history and book return dates.
   Loan renewal: option to renew book loans from the app without visiting the library in person.
   Book reservation: allow reserving books available in the library (or, when they are on loan, adding them to a
   waiting list).
1. University Services
   Student Wellbeing space reservations: schedule appointments or reserve spaces in Student Wellbeing activities or
   facilities.
   Parking management: reserve parking spots, manage access and, if needed, pay for parking use.
   Academic tutoring reservations: schedule an appointment for academic support with professors or tutors.
   Academic coordination appointments: schedule a meeting with academic coordination staff to resolve questions.
   Psychology appointments: book an appointment with the psychology or emotional wellbeing service.
1. Events and Activities
   Event view: access a calendar of university events, including cultural, sports and academic activities.
   Event filtering by category: ability to filter events by type (cultural, academic, sports, and so on).
   Add events to the personal calendar: allow users to add events to their mobile device calendar.
   Event registration: register directly for university events (when required).
1. Location and Orientation
   Interactive campus map: view a map of the university with the location of buildings, classrooms, libraries and
   other relevant services.
   Interactive guide or orientation video: provide guides or interactive videos explaining how to reach different
   places within the campus.
   Real-time location: feature that shows the user's current location within the campus for real-time orientation.
1. Social Networks
   Social network links: direct access to the university's official social networks (Facebook, Instagram, Twitter,
   and so on).
   Recent post view: show the latest posts or featured events directly inside the app (for example, news and
   university updates)
1. Support and Help
   In-app support center: a support system that lets users resolve questions or technical issues, either through a
   knowledge base or direct contact with support staff.
   Feedback and suggestions: allow users to send comments, suggestions or bug reports easily and directly.

1. Accessibility and Customization
   Accessibility mode: include accessibility options for people with disabilities (for example, text enlargement,
   contrast, screen reader).
   Interface customization: allow users to customize the app's appearance (themes, font sizes, and so on) to improve
   the usage experience.
1. Security and Privacy
   Secure authentication: implement a robust authentication system (such as two-factor authentication) to protect
   the user account.
   Personal data protection: ensure sensitive data (such as grades, schedules and personal information) is encrypted
   and protected according to data security regulations.
   Access history and secure session: give users a record of active devices or sessions, and allow them to log out
   remotely.
1. Integrations
   Integration with other university systems: integrate the app with systems already in place at the university,
   such as learning platforms, payment management or academic management systems, to improve data synchronization.

1. Users and Roles

1. Student
   The Student is the application's primary user and has access to most academic features and services related to
   university life.
   Permissions:
   Authentication:
   Login with university credentials (student code and password).
   Limited guest access (access to public features).
   Academic Management:
   View the current academic schedule and the details of each class.
   View grades for the current semester and the grade history.
   Library:
   Access the personal library record (borrowed books and return dates).
   Renew book loans from the app.
   Reserve available books or add books to a waiting list.
   University Services:
   Reserve spaces at Student Wellbeing, parking, academic tutoring, academic coordination and psychology
   appointments.
   Events and Activities:
   View events organized by the university, filter them by category (cultural, academic, sports, and so on) and add
   them to the personal calendar.
   Register for university events when required.
   Virtual ID Card:
   View the digital university ID card and generate a QR code for identification within the university.
   Communication:
   Access a chat system to communicate with other students.
   Receive push notifications about events, schedule changes, messages, and so on.
   Location and Orientation:
   View the interactive campus map and the location of buildings and relevant spaces.
   Access interactive guides or videos about how to reach certain places within the campus.
   Social Networks:
   Access the university's official social networks.

1. Professor
   The Professor has access to features related to academic management, communication with students and some
   administrative services.
   Permissions:
   Authentication:
   Login with academic credentials (professor code and password).
   Academic Management:
   View the academic schedule of the classes they teach.
   View the list of students enrolled in their classes.
   View student grades and upload new grades.
   Update the class schedule or make adjustments when changes occur.
   Communication:
   Access the chat system to communicate with their students.
   Send notifications to students about class changes or important events.
   University Services:
   Schedule academic tutoring sessions with students.
   Events and Activities:
   Create or manage academic or cultural events organized by the university.
   View events relevant to their area of work (academic activities, meetings, and so on).
   Social Networks:
   Access the university's official social networks to stay informed.

1. Administrator
   The Administrator is the user with full access to application management and administration. They can manage
   configuration and users, and supervise every university process within the app.
   Permissions:
   User Management:
   Create, edit and delete student, professor and administrative staff accounts.
   Manage user roles and permissions (assign administrator, professor and other roles).
   Academic Management:
   Modify academic schedules globally.
   View and edit grades of every student in the university.
   Manage the assignment of educational resources, such as classrooms or technical equipment.
   University Services:
   Manage space reservations at Student Wellbeing and parking.
   View appointments and reservations made by students and professors.
   Administer psychology, academic tutoring and academic coordination services.
   Events and Activities:
   Create, edit and manage events and activities within the university.
   View event registration details and control access to them.
   Communication:
   Send notifications to all users about important events, schedule updates, new services, and so on.
   Security and Configuration:
   Configure and manage application security (data encryption, two-factor authentication, and so on).
   Perform backups and system maintenance.
   Social Networks:
   Manage links and content related to the university's official social networks.
   Reports and Analytics:
   Access detailed reports on application usage, student interaction with services and academic performance.

1. Administrative Staff
   Administrative Staff hold an intermediate role, with access to certain administrative and support services, but
   with limited permissions compared to administrators.
   Permissions:
   Academic Management:
   View schedules and the list of students enrolled in classes, without the ability to modify grades or schedules.
   Manage resource assignment for academic activities (spaces, materials, and so on).
   University Services:
   Manage student appointments for services such as student wellbeing, psychology, tutoring and academic
   coordination.
   Monitor parking and other facility availability.
   Events and Activities:
   Create or manage events at the administrative level, such as wellbeing activities or general academic events.
   Manage event registration and attendance.
   Communication:
   Communicate with students and professors through notifications or messages about events, appointments or
   important updates.
   Social Networks:
   Access the university's social network information and support event promotion.

1. Guest (Unauthenticated User)
   The Guest is a user without full access to the app, but who can view certain features of general interest, such
   as public events, basic information services or open university content.
   Permissions:
   Limited Access:
   View public events organized by the university.
   Consult general information about the university, such as location and contact details.
   Social Networks:
   Access the university's official social networks without authentication.

1. Platform and Technologies
   Specify which platforms the application will run on (web, mobile, desktop) and which technologies will be used.
1. Design and User Experience (UI/UX)
   Describe the appearance and usability of the application.
1. Integrations
   Specify systems or services the application must integrate with.
1. Security
   Define the required security measures, such as authentication, encryption and data protection.
1. Non-Functional Requirements
   Describe performance, scalability and availability requirements, among others.
1. Delivery and Schedule
   Define the development plan and delivery dates for each phase.

RESOURCES & SOURCES:
VIDEO: Paper Prototype Jave Móvil
