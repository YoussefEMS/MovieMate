# PACKAGES
import os
import time
import csv
import requests
import pandas as pd
import streamlit as st
import plotly.express as px
import plotly.graph_objects as go
from datetime import datetime

# Function to list all files in the "conversations" folder
def list_files_in_folder(folder_path):
    files = []
    for file in os.listdir(folder_path):
        if os.path.isfile(os.path.join(folder_path, file)):
            files.append(file)
    return files

# Function to communicate with Scala backend
def communicate_with_scala(user_input):
    max_retries = 3
    retry_delay = 2
    input_file = 'src/main/connection/pyinput.txt'
    output_file = 'src/main/connection/scalaoutput.txt'

    try:
        # Write input
        with open(input_file, 'w', encoding='utf-8') as file:
            file.write(user_input + '\n')
        
        # Wait and retry logic
        for attempt in range(max_retries):
            time.sleep(retry_delay)  # Wait for Scala to process
            
            try:
                with open(output_file, 'r', encoding='utf-8') as file:
                    scala_output = file.read().strip()
                    if scala_output:
                        # Only clear the file if we successfully got a response
                        with open(output_file, 'w', encoding='utf-8') as clear_file:
                            clear_file.write('')
                        return scala_output
            except Exception as e:
                if attempt == max_retries - 1:  # Last attempt
                    st.error(f"Error reading Scala response: {str(e)}")
                    return "I'm having trouble processing that. Could you try again?"
                continue  # Try again if not last attempt
        
        return "I'm having trouble processing that. Could you try again?"
    
    except Exception as e:
        st.error(f"Error communicating with backend: {str(e)}")
        return "I'm having trouble processing that. Could you try again?"

# Function to load and process quiz data
def load_quiz_data():
    df = pd.read_csv('quiz_responses.csv')
    df['answered_at'] = pd.to_datetime(df['answered_at'])
    df['date'] = df['answered_at'].dt.date
    return df

def create_quiz_visualizations(df):
    # Analytics type selector
    analytics_type = st.selectbox(
        "Select Analytics View",
        ["Overall Performance", "Category Performance", "Time Analysis", "Daily Performance"]
    )

    if analytics_type == "Overall Performance":
        # Enhanced Pie Chart with language type colors
        # First, get performance by language type
        lang_performance = df[df['type'].str.contains('english|arabic', case=False)].copy()
        lang_performance['language'] = lang_performance['type'].apply(
            lambda x: 'English' if 'english' in x.lower() else 'Arabic'
        )
        lang_stats = lang_performance.groupby('language')['is_correct'].agg(['count', 'mean']).reset_index()
        lang_stats['percentage'] = (lang_stats['mean'] * 100).round(1)
        
        # Create pie chart with custom colors
        fig_lang = go.Figure(data=[go.Pie(
            labels=lang_stats['language'],
            values=lang_stats['count'],
            text=lang_stats['percentage'].apply(lambda x: f'{x}%'),
            textposition='outside',
            marker=dict(colors=['#FF9999', '#66B2FF']),
            textinfo='label+text',
            hole=0.3
        )])
        fig_lang.update_layout(
            title='Performance by Language',
            annotations=[dict(text='Language\nDistribution', x=0.5, y=0.5, font_size=12, showarrow=False)]
        )
        st.plotly_chart(fig_lang)

        # Overall correct vs incorrect
        correct_answers = df['is_correct'].value_counts()
        overall_percentage = (df['is_correct'].mean() * 100).round(1)
        
        fig_performance = go.Figure(data=[go.Pie(
            labels=['Correct', 'Incorrect'],
            values=[correct_answers.get(True, 0), correct_answers.get(False, 0)],
            text=[f'{overall_percentage}%', f'{(100-overall_percentage)}%'],
            textposition='outside',
            marker=dict(colors=['#4ecdc4', '#ff6b6b']),
            textinfo='label+text',
            hole=0.3
        )])
        fig_performance.update_layout(
            title='Overall Quiz Performance',
            annotations=[dict(text='Success\nRate', x=0.5, y=0.5, font_size=12, showarrow=False)]
        )
        st.plotly_chart(fig_performance)

    elif analytics_type == "Category Performance":
        # Performance by Category
        category_performance = df.groupby('type')['is_correct'].agg(['count', 'mean']).reset_index()
        category_performance['mean'] = category_performance['mean'] * 100
        category_performance['mean'] = category_performance['mean'].round(1)
        
        fig_category = go.Figure(data=[
            go.Bar(
                x=category_performance['type'],
                y=category_performance['mean'],
                text=category_performance['mean'].apply(lambda x: f'{x}%'),
                textposition='auto',
                marker_color='#4ecdc4'
            )
        ])
        fig_category.update_layout(
            title='Performance by Category',
            xaxis_title='Category',
            yaxis_title='Correct Answers (%)',
            yaxis_range=[0, 100]
        )
        st.plotly_chart(fig_category)

    elif analytics_type == "Time Analysis":
        # Response Time Analysis
        df['hour'] = df['answered_at'].dt.hour
        hourly_activity = df.groupby('hour')['question'].count().reset_index()
        
        fig_time = go.Figure(data=[
            go.Scatter(
                x=hourly_activity['hour'],
                y=hourly_activity['question'],
                mode='lines+markers',
                line=dict(color='#4ecdc4'),
                marker=dict(size=8)
            )
        ])
        fig_time.update_layout(
            title='Quiz Activity by Hour',
            xaxis_title='Hour of Day',
            yaxis_title='Number of Responses',
            xaxis=dict(tickmode='linear', tick0=0, dtick=1)
        )
        st.plotly_chart(fig_time)

    else:  # Daily Performance
        # Daily performance analysis
        daily_stats = df.groupby('date').agg({
            'is_correct': ['count', 'mean'],
            'question': 'count'
        }).reset_index()
        daily_stats.columns = ['date', 'total_answers', 'success_rate', 'questions']
        daily_stats['success_rate'] = (daily_stats['success_rate'] * 100).round(1)
        
        fig_daily = go.Figure(data=[
            go.Scatter(
                x=daily_stats['date'],
                y=daily_stats['success_rate'],
                mode='lines+markers',
                name='Success Rate',
                line=dict(color='#4ecdc4'),
                marker=dict(size=8)
            ),
            go.Bar(
                x=daily_stats['date'],
                y=daily_stats['questions'],
                name='Number of Questions',
                marker_color='rgba(158,202,225,0.4)',
                yaxis='y2'
            )
        ])
        
        fig_daily.update_layout(
            title='Daily Performance Overview',
            xaxis_title='Date',
            yaxis_title='Success Rate (%)',
            yaxis2=dict(
                title='Number of Questions',
                overlaying='y',
                side='right'
            ),
            legend=dict(
                orientation="h",
                yanchor="bottom",
                y=1.02,
                xanchor="right",
                x=1
            )
        )
        st.plotly_chart(fig_daily)

        # Display daily statistics table
        st.subheader("Daily Statistics")
        st.dataframe(
            daily_stats.style.format({
                'success_rate': '{:.1f}%',
                'total_answers': '{:.0f}',
                'questions': '{:.0f}'
            })
        )

# MAIN + STREAMLIT PAGE LAYOUT
def main():
    # Page Config
    st.set_page_config(page_title="MovieMate AI", page_icon="🎬", layout="wide")
    
    # Sidebar Configuration
    st.sidebar.title("Navigation")
    page = st.sidebar.radio("Select View", ["Chat Interface", "Analytics Dashboard"])
    
    # Page title
    st.title("🎬 MovieMate AI")
    
    if page == "Chat Interface":
        st.subheader("Your personal guide to movies and TV shows")
        
        # Initialize conversation history if not present
        if "conversation" not in st.session_state:
            st.session_state.conversation = None

        # Initialize memory for storing past questions and answers
        if "chat_memory" not in st.session_state:
            st.session_state.chat_memory = []

        # ROLES FOR CHAT MEMORY
        USER = "user"
        ASSISTANT = "assistant"   
        
        # Create conversations directory if it doesn't exist
        conversations_dir = 'conversations/'
        if not os.path.exists(conversations_dir):
            os.makedirs(conversations_dir)
            
        # Create current chat tracker file if it doesn't exist
        if not os.path.exists(conversations_dir + 'currentchat.txt'):
            with open(conversations_dir + 'currentchat.txt', 'w') as file:
                file.write('chathistory_0.txt')
                
        # Get conversation files
        conversation_files = list_files_in_folder(conversations_dir)
        if 'currentchat.txt' in conversation_files:
            conversation_files.remove('currentchat.txt')
            
        # Create first chat file if none exist
        if len(conversation_files) == 0:
            open(conversations_dir + 'chathistory_0.txt', 'w')
            conversation_files = ['chathistory_0.txt']

        # Get current chat file
        with open(conversations_dir + 'currentchat.txt', 'r') as file:
            current_chat = file.readline().strip()

        # USER INPUT
        user_question = st.chat_input("Ask about movies, shows, actors, or take a quiz...")
        
        # Display past questions and answers
        for interaction in st.session_state.chat_memory:
            st.chat_message(USER).write(interaction['question'])
            st.chat_message(ASSISTANT).write(interaction['answer'])
        
        if user_question:
            with st.spinner("Thinking about movies..."):
                response = communicate_with_scala(user_question)
                st.chat_message(USER).write(user_question)
                st.chat_message(ASSISTANT).write(response)
                st.session_state.chat_memory.append({'question': user_question, 'answer': response})
                
                # Save the question to the current conversation file
                with open(conversations_dir + current_chat, 'a') as file:
                    file.write(user_question + '\n')

    else:  # Analytics Dashboard
        st.subheader("Quiz Performance Analytics")
        
        try:
            # Load and display analytics in full width
            quiz_data = load_quiz_data()
            
            # Create three columns for the first row of visualizations
            col1, col2 = st.columns(2)
            
            with col1:
                # Language distribution pie chart
                lang_performance = quiz_data[quiz_data['type'].str.contains('english|arabic', case=False)].copy()
                lang_performance['language'] = lang_performance['type'].apply(
                    lambda x: 'English' if 'english' in x.lower() else 'Arabic'
                )
                lang_stats = lang_performance.groupby('language')['is_correct'].agg(['count', 'mean']).reset_index()
                lang_stats['percentage'] = (lang_stats['mean'] * 100).round(1)
                
                fig_lang = go.Figure(data=[go.Pie(
                    labels=lang_stats['language'],
                    values=lang_stats['count'],
                    text=lang_stats['percentage'].apply(lambda x: f'{x}%'),
                    textposition='outside',
                    marker=dict(colors=['#FF9999', '#66B2FF']),
                    textinfo='label+text',
                    hole=0.3
                )])
                fig_lang.update_layout(
                    title='Performance by Language',
                    height=400,
                    annotations=[dict(text='Language\nDistribution', x=0.5, y=0.5, font_size=12, showarrow=False)]
                )
                st.plotly_chart(fig_lang, use_container_width=True)

            with col2:
                # Overall performance pie chart
                correct_answers = quiz_data['is_correct'].value_counts()
                overall_percentage = (quiz_data['is_correct'].mean() * 100).round(1)
                
                fig_performance = go.Figure(data=[go.Pie(
                    labels=['Correct', 'Incorrect'],
                    values=[correct_answers.get(True, 0), correct_answers.get(False, 0)],
                    text=[f'{overall_percentage}%', f'{(100-overall_percentage)}%'],
                    textposition='outside',
                    marker=dict(colors=['#4ecdc4', '#ff6b6b']),
                    textinfo='label+text',
                    hole=0.3
                )])
                fig_performance.update_layout(
                    title='Overall Quiz Performance',
                    height=400,
                    annotations=[dict(text='Success\nRate', x=0.5, y=0.5, font_size=12, showarrow=False)]
                )
                st.plotly_chart(fig_performance, use_container_width=True)

            # Full width for category performance
            category_performance = quiz_data.groupby('type')['is_correct'].agg(['count', 'mean']).reset_index()
            category_performance['mean'] = category_performance['mean'] * 100
            category_performance['mean'] = category_performance['mean'].round(1)
            
            fig_category = go.Figure(data=[
                go.Bar(
                    x=category_performance['type'],
                    y=category_performance['mean'],
                    text=category_performance['mean'].apply(lambda x: f'{x}%'),
                    textposition='auto',
                    marker_color='#4ecdc4'
                )
            ])
            fig_category.update_layout(
                title='Performance by Category',
                xaxis_title='Category',
                yaxis_title='Correct Answers (%)',
                height=400,
                yaxis_range=[0, 100]
            )
            st.plotly_chart(fig_category, use_container_width=True)

            # Create two columns for time-based analytics
            col3, col4 = st.columns(2)
            
            with col3:
                # Hourly activity
                quiz_data['hour'] = quiz_data['answered_at'].dt.hour
                hourly_activity = quiz_data.groupby('hour')['question'].count().reset_index()
                
                fig_time = go.Figure(data=[
                    go.Scatter(
                        x=hourly_activity['hour'],
                        y=hourly_activity['question'],
                        mode='lines+markers',
                        line=dict(color='#4ecdc4'),
                        marker=dict(size=8)
                    )
                ])
                fig_time.update_layout(
                    title='Quiz Activity by Hour',
                    xaxis_title='Hour of Day',
                    yaxis_title='Number of Responses',
                    height=400,
                    xaxis=dict(tickmode='linear', tick0=0, dtick=1)
                )
                st.plotly_chart(fig_time, use_container_width=True)

            with col4:
                # Daily performance
                daily_stats = quiz_data.groupby('date').agg({
                    'is_correct': ['count', 'mean'],
                    'question': 'count'
                }).reset_index()
                daily_stats.columns = ['date', 'total_answers', 'success_rate', 'questions']
                daily_stats['success_rate'] = (daily_stats['success_rate'] * 100).round(1)
                
                fig_daily = go.Figure(data=[
                    go.Scatter(
                        x=daily_stats['date'],
                        y=daily_stats['success_rate'],
                        mode='lines+markers',
                        name='Success Rate',
                        line=dict(color='#4ecdc4'),
                        marker=dict(size=8)
                    ),
                    go.Bar(
                        x=daily_stats['date'],
                        y=daily_stats['questions'],
                        name='Number of Questions',
                        marker_color='rgba(158,202,225,0.4)',
                        yaxis='y2'
                    )
                ])
                
                fig_daily.update_layout(
                    title='Daily Performance Overview',
                    xaxis_title='Date',
                    yaxis_title='Success Rate (%)',
                    height=400,
                    yaxis2=dict(
                        title='Number of Questions',
                        overlaying='y',
                        side='right'
                    ),
                    legend=dict(
                        orientation="h",
                        yanchor="bottom",
                        y=1.02,
                        xanchor="right",
                        x=1
                    )
                )
                st.plotly_chart(fig_daily, use_container_width=True)

            # Display daily statistics table at the bottom
            st.subheader("Daily Statistics")
            st.dataframe(
                daily_stats.style.format({
                    'success_rate': '{:.1f}%',
                    'total_answers': '{:.0f}',
                    'questions': '{:.0f}'
                }),
                use_container_width=True
            )

        except Exception as e:
            st.error(f"Error loading quiz data: {str(e)}")

if __name__ == '__main__':
    main()